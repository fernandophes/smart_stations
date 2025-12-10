package br.edu.ufersa.cc.seg.device;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import br.edu.ufersa.cc.seg.common.factories.CryptoServiceFactory;
import br.edu.ufersa.cc.seg.common.factories.EnvOrInputFactory;
import br.edu.ufersa.cc.seg.common.factories.MessageFactory;
import br.edu.ufersa.cc.seg.common.factories.MessengerFactory;
import br.edu.ufersa.cc.seg.common.factories.ServerMessengerFactory;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.Messenger;
import br.edu.ufersa.cc.seg.common.messengers.SecureMessenger;
import br.edu.ufersa.cc.seg.common.utils.Constants;
import br.edu.ufersa.cc.seg.common.utils.Element;
import br.edu.ufersa.cc.seg.common.utils.Fields;
import br.edu.ufersa.cc.seg.common.utils.MessageType;
import br.edu.ufersa.cc.seg.common.utils.ServerType;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class Device {

    private static final Random RANDOM = new Random();
    private static final long INTERVAL = 3_000;
    private static final Timer TIMER = new Timer();

    private final String name;
    private final EnvOrInputFactory envOrInputFactory;

    private Messenger locationMessenger;
    private Messenger authMessenger;
    private SecureMessenger edgeMessenger;

    private TimerTask subscription;

    private boolean isRunning;

    public void start() {
        connectToLocationServer();
        locateAuthServer();
        final var token = authenticate();
        locateEdgeServer();

        subscription = new TimerTask() {
            @Override
            public void run() {
                final var snapshot = simulateReading()
                        .withValue("token", token);
                log.info("Leitura feita: {}", snapshot);
                edgeMessenger.send(snapshot);
            }
        };

        TIMER.schedule(subscription, Date.from(Instant.now()), INTERVAL);
        setRunning(true);
    }

    @SneakyThrows
    public void close() {
        if (isRunning()) {
            subscription.cancel();
            log.info("Atividade do dispositivo {} finalizada", name);
        }

        envOrInputFactory.close();
        edgeMessenger.close();
        locationMessenger.close();
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
        insecureMessenger.close();
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
    private void locateEdgeServer() {
        log.info("Localizando servidor de borda...");

        final var request = new Message(MessageType.LOCATE_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.EDGE);

        do {
            locationMessenger.send(request);
            final var response = locationMessenger.receive();

            if (response.getType().equals(MessageType.OK)) {
                log.info("Servidor de borda localizado! Contatando com criptografia assimétrica...");
                edgeMessenger = useSymmetric(response);
                log.info("Recebidos dados para criptografia simétrica. Conexão atualizada.");
            }

            if (edgeMessenger == null) {
                Thread.sleep(INTERVAL);
            }
        } while (edgeMessenger == null);
    }

    @SneakyThrows
    private SecureMessenger useSymmetric(final Message locationResponse) {
        final var asymmetricHost = (String) locationResponse.getValues().get(Fields.HOST);
        final var asymmetricPort = (int) locationResponse.getValues().get(Fields.PORT);
        final var publicKey = (String) locationResponse.getValues().get(Fields.PUBLIC_KEY);

        final var asymmetricCryptoService = CryptoServiceFactory.publicRsa(publicKey);
        final var asymmetricMessenger = MessengerFactory.secureUdp(asymmetricHost, asymmetricPort,
                asymmetricCryptoService);

        final var request = new Message(MessageType.USE_SYMMETRIC);
        asymmetricMessenger.send(request);

        final var response = asymmetricMessenger.receive();
        final int symmetricPort = response.getValue(Fields.PORT);
        final String encryptionKey = response.getValue(Fields.ENCRYPTION_KEY);
        final String hmacKey = response.getValue(Fields.HMAC_KEY);

        final var symmetricCryptoService = CryptoServiceFactory.aes(encryptionKey, hmacKey);
        return MessengerFactory.secureUdp(asymmetricHost, symmetricPort, symmetricCryptoService);
    }

    private Message simulateReading() {
        final var snapshot = new Message(MessageType.SEND_SNAPSHOT)
                .withValue("deviceName", getName())
                .withValue("timestamp", LocalDateTime.now().format(Constants.DATE_TIME_FORMATTER));

        for (final var element : Element.values()) {
            final var raw = RANDOM.nextDouble(element.getMin(), element.getMax());
            final var scaled = BigDecimal.valueOf(raw).setScale(element.getScale(), RoundingMode.HALF_DOWN);

            snapshot.withValue(element.name(), scaled);
        }

        return snapshot;
    }

}
