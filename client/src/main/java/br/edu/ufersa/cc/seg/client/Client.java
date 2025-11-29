package br.edu.ufersa.cc.seg.client;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.util.EntityUtils;

import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.crypto.SecureMessage;
import br.edu.ufersa.cc.seg.common.factories.CryptoServiceFactory;
import br.edu.ufersa.cc.seg.common.factories.EnvOrInputFactory;
import br.edu.ufersa.cc.seg.common.factories.MessengerFactory;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.Messenger;
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
            public void run() {
                final var time3SecondsAgo = LocalDateTime.now().minusSeconds(INTERVAL / 1000)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                final var response = myClient.getSnapshotsAfter(token, time3SecondsAgo);

                try {
                    final var entity = response.getEntity();
                    final var json = EntityUtils.toString(entity);
                    final var secureMessage = SecureMessage.fromJson(json);

                    final var messageAsBytes = symmetricCryptoService.decrypt(secureMessage);
                    final var message = Message.fromBytes(messageAsBytes);
                    final var data = message.getValues().get("data");

                    log.info("Leituras feitas: {}", data);
                } catch (IOException e) {
                    // Ignorar
                }
            }
        };

        TIMER.schedule(subscription, Date.from(Instant.now()), INTERVAL);
    }

    @SneakyThrows
    private void connectToLocationServer() {
        final var locationHost = envOrInputFactory.getString("LOCATION_HOST");
        final var locationPort = envOrInputFactory.getInt("LOCATION_PORT");

        locationMessenger = MessengerFactory.udp(locationHost, locationPort);
    }

    @SneakyThrows
    private void locateAuthServer() {
        final var request = new Message(MessageType.LOCATE_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.AUTH);

        do {
            locationMessenger.send(request);
            final var response = locationMessenger.receive();

            if (response.getType().equals(MessageType.OK)) {
                final var host = (String) response.getValues().get(Fields.HOST);
                final var port = (int) response.getValues().get(Fields.PORT);

                authMessenger = MessengerFactory.tcp(host, port);
            }

            if (authMessenger == null) {
                Thread.sleep(INTERVAL);
            }
        } while (authMessenger == null);
    }

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
            useSymmetric(response, token);
            log.info("Recebidos dados para criptografia simétrica. Conexão atualizada.");
        }
    }

    @SneakyThrows
    private void useSymmetric(final Message locationResponse, final String token) {
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
            final var encryptionKey = (String) message.getValues().get("ENCRYPTION_KEY");
            final var hmacKey = (String) message.getValues().get("HMAC_KEY");

            symmetricCryptoService = CryptoServiceFactory.aes(encryptionKey, hmacKey);
        } else {
            throw new RuntimeException((String) message.getValues().get("message"));
        }

    }

}
