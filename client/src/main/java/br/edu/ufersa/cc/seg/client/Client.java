package br.edu.ufersa.cc.seg.client;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.crypto.HybridCryptoException;
import br.edu.ufersa.cc.seg.common.crypto.SecureMessage;
import br.edu.ufersa.cc.seg.common.dto.SnapshotDto;
import br.edu.ufersa.cc.seg.common.factories.CryptoServiceFactory;
import br.edu.ufersa.cc.seg.common.factories.EnvOrInputFactory;
import br.edu.ufersa.cc.seg.common.factories.MessageFactory;
import br.edu.ufersa.cc.seg.common.factories.MessengerFactory;
import br.edu.ufersa.cc.seg.common.factories.ServerMessengerFactory;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.Messenger;
import br.edu.ufersa.cc.seg.common.utils.Constants;
import br.edu.ufersa.cc.seg.common.utils.Fields;
import br.edu.ufersa.cc.seg.common.utils.MessageType;
import br.edu.ufersa.cc.seg.common.utils.ServerType;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class Client {

    private static final long INTERVAL = 3_000;
    private static final Timer TIMER = new Timer();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;
    private final EnvOrInputFactory envOrInputFactory;

    private Messenger locationMessenger;
    private Messenger authMessenger;
    private MyHttpClient myClient;

    private TimerTask subscription;
    private CryptoService symmetricCryptoService;

    @SneakyThrows
    public void start() {
        connectToLocationServer();
        locateAuthServer();
        final var token = authenticate();
        locateDatacenterHttp(token);

        subscription = new TimerTask() {
            @Override
            @SuppressWarnings("unchecked")
            public void run() {
                final var time3SecondsAgo = LocalDateTime.now().minusSeconds(INTERVAL / 1000)
                        .format(Constants.DATE_TIME_URL_FORMATTER);
                final var response = myClient.getSnapshotsAfter(token, time3SecondsAgo);

                try {
                    final var entity = response.getEntity();
                    final var secureJson = EntityUtils.toString(entity);
                    final var secureMessage = SecureMessage.fromJson(secureJson);

                    final var messageAsBytes = symmetricCryptoService.decrypt(secureMessage);
                    final var message = Message.fromBytes(messageAsBytes);
                    final var data = (List<SnapshotDto>) message.getValues().get("data");
                    final var json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data);

                    log.info("Leituras feitas: {}", json);
                } catch (IOException e) {
                    // Ignorar
                }
            }
        };

        TIMER.schedule(subscription, Date.from(Instant.now()), INTERVAL);
    }

    @SneakyThrows
    public void stop() {
        if (subscription != null) {
            subscription.cancel();
        }
        locationMessenger.close();
        authMessenger.close();
    }

    @SneakyThrows
    private void connectToLocationServer() {
        /*
         * FASE 1
         */
        // Abrir servidor RSA temporário, pra receber as chaves AES
        final var rsaPair = CryptoServiceFactory.rsaPair();
        final var asymmetricMessenger = ServerMessengerFactory.secureUdp(rsaPair.getPrivateSide());
        asymmetricMessenger.subscribe(message -> {
            /*
             * FASE 3
             */
            // Abrir conexão AES permanente
            final String locationHost = message.getValue(Fields.HOST);
            final int locationPort = message.getValue(Fields.PORT);
            final String encryptionKey = message.getValue(Fields.ENCRYPTION_KEY);
            final String hmacKey = message.getValue(Fields.HMAC_KEY);

            // Salvar conexão no server
            final var cryptoService = CryptoServiceFactory.aes(encryptionKey, hmacKey);
            locationMessenger = MessengerFactory.secureUdp(locationHost, locationPort, cryptoService);

            return MessageFactory.ok();
        });

        /*
         * FASE 2
         */
        // Enviar chave pública via conexão insegura (plain text)
        final var insecureHost = envOrInputFactory.getString("LOCATION_HOST");
        final var insecurePort = envOrInputFactory.getInt("LOCATION_PORT");
        final var insecureMessenger = MessengerFactory.udp(insecureHost, insecurePort);
        final var insecureRequest = new Message(MessageType.USE_SYMMETRIC)
                .withValue(Fields.HOST, InetAddress.getLocalHost().getHostAddress())
                .withValue(Fields.PORT, asymmetricMessenger.getPort())
                .withValue(Fields.PUBLIC_KEY, rsaPair.getPublicKey().getEncoded());
        insecureMessenger.send(insecureRequest);
        final var insecureResponse = insecureMessenger.receive();

        log.info("Conexão com Location Server: \n{}", insecureResponse.toJson());

        /*
         * FASE 4
         */
        asymmetricMessenger.close();
    }

    @SneakyThrows
    private void locateAuthServer() {
        final var locateRequest = new Message(MessageType.LOCATE_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.AUTH);

        do {
            locationMessenger.send(locateRequest);
            final var locateResponse = locationMessenger.receive();

            if (locateResponse.getType().equals(MessageType.OK)) {
                // Abrir messenger assimétrico
                final String rsaHost = locateResponse.getValue(Fields.HOST);
                final int rsaPort = locateResponse.getValue(Fields.PORT);
                final String rsaPublicKey = locateResponse.getValue(Fields.PUBLIC_KEY);
                final var rsaCryptoService = CryptoServiceFactory.publicRsa(rsaPublicKey);
                final var rsaMessenger = MessengerFactory.secureTcp(rsaHost, rsaPort, rsaCryptoService);

                // Solicitar conexão simétrica
                final var useSymmetricRequest = new Message(MessageType.USE_SYMMETRIC);
                rsaMessenger.send(useSymmetricRequest);
                final var useSymmetricResponse = rsaMessenger.receive();

                // Abrir messenger simétrico
                final String aesHost = useSymmetricResponse.getValue(Fields.HOST);
                final int aesPort = useSymmetricResponse.getValue(Fields.PORT);
                final String aesEncryptionKey = useSymmetricResponse.getValue(Fields.ENCRYPTION_KEY);
                final String aesHmacKey = useSymmetricResponse.getValue(Fields.HMAC_KEY);
                final var aesCryptoService = CryptoServiceFactory.aes(aesEncryptionKey, aesHmacKey);
                final var aesMessenger = MessengerFactory.secureTcp(aesHost, aesPort, aesCryptoService);

                authMessenger = aesMessenger;
            }

            if (authMessenger == null) {
                Thread.sleep(INTERVAL);
            }
        } while (authMessenger == null);
    }

    @SneakyThrows
    private String authenticate() {
        final var identifier = envOrInputFactory.getString("IDENTIFIER");
        final var secret = envOrInputFactory.getString("SECRET");

        final var request = new Message(MessageType.AUTHENTICATE)
                .withValue("identifier", identifier)
                .withValue("secret", secret);

        authMessenger.send(request);
        final var response = authMessenger.receive();

        return (String) response.getValues().get("token");
    }

    @SneakyThrows
    private void locateDatacenterHttp(final String token) {
        log.info("Localizando Datacenter...");

        final var request = new Message(MessageType.LOCATE_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.HTTP);

        locationMessenger.send(request);
        final var response = locationMessenger.receive();

        if (response.getType().equals(MessageType.OK)) {
            log.info("Datacenter localizado! Contatando com criptografia assimétrica...");
            connectToDatacenterHttp(response, token);
            log.info("Recebidos dados para criptografia simétrica. Conexão atualizada.");
        }
    }

    @SneakyThrows
    private void connectToDatacenterHttp(final Message locationResponse, final String token) {
        final var host = (String) locationResponse.getValues().get(Fields.HOST);
        final var port = (int) locationResponse.getValues().get(Fields.PORT);
        final var publicKey = (String) locationResponse.getValues().get(Fields.PUBLIC_KEY);
        final var asymmetricCryptoService = CryptoServiceFactory.publicRsa(publicKey);

        myClient = new MyHttpClient(host, port);

        final var response = myClient.useSymmetric(token);
        final var entity = response.getEntity();
        final var json = EntityUtils.toString(entity);
        final var secureMessage = SecureMessage.fromJson(json);
        final var messageAsBytes = asymmetricCryptoService.decrypt(secureMessage);
        final var message = Message.fromBytes(messageAsBytes);

        if (MessageType.OK.equals(message.getType())) {
            final var encryptionKey = (String) message.getValues().get(Fields.ENCRYPTION_KEY);
            final var hmacKey = (String) message.getValues().get(Fields.HMAC_KEY);

            symmetricCryptoService = CryptoServiceFactory.aes(encryptionKey, hmacKey);
        } else {
            throw new HybridCryptoException((String) message.getValues().get("message"));
        }

    }

}
