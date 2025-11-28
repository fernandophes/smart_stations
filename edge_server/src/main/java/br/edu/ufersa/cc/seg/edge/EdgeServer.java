package br.edu.ufersa.cc.seg.edge;

import java.io.IOException;
import java.net.InetAddress;
import java.security.PublicKey;

import br.edu.ufersa.cc.seg.common.auth.TokenService;
import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.factories.CryptoServiceFactory;
import br.edu.ufersa.cc.seg.common.factories.EnvOrInputFactory;
import br.edu.ufersa.cc.seg.common.factories.MessageFactory;
import br.edu.ufersa.cc.seg.common.factories.MessengerFactory;
import br.edu.ufersa.cc.seg.common.factories.ServerMessengerFactory;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.Messenger;
import br.edu.ufersa.cc.seg.common.messengers.SecureMessenger;
import br.edu.ufersa.cc.seg.common.messengers.ServerMessenger;
import br.edu.ufersa.cc.seg.common.utils.Fields;
import br.edu.ufersa.cc.seg.common.utils.InstanceType;
import br.edu.ufersa.cc.seg.common.utils.MessageType;
import br.edu.ufersa.cc.seg.common.utils.ServerType;
import br.edu.ufersa.cc.seg.common.utils.TokenHandler;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EdgeServer {

    private static final long INTERVAL = 3_000;

    private final CryptoService asymmetricCryptoService;
    private final TokenService tokenService;
    private final CryptoService oldCryptoService;
    private final EnvOrInputFactory envOrInputFactory;

    private final PublicKey publicEncryptionKey;

    private final ServerMessenger serverMessenger;
    private SecureMessenger datacenterMessenger;
    private Messenger locationMessenger;

    public EdgeServer(final CryptoService cryptoService, final EnvOrInputFactory envOrInputFactory)
            throws IOException {
        this.oldCryptoService = cryptoService;
        this.envOrInputFactory = envOrInputFactory;

        final var rsaPair = CryptoServiceFactory.rsaPair();
        this.publicEncryptionKey = rsaPair.getPublicKey();

        this.asymmetricCryptoService = rsaPair.getPrivateSide();
        this.serverMessenger = ServerMessengerFactory.secureUdp(asymmetricCryptoService);

        final var jwtSecret = envOrInputFactory.getString("JWT_SECRET");
        this.tokenService = new TokenService(jwtSecret);
    }

    public void start() {
        connectToLocationServer();
        register();
        locateDatacenterServer();
        serverMessenger.subscribe(this::useSymmetric);
    }

    @SneakyThrows
    public void stop() {
        datacenterMessenger.close();
        locationMessenger.close();
        serverMessenger.close();
    }

    @SneakyThrows
    private void connectToLocationServer() {
        final var locationHost = envOrInputFactory.getString("LOCATION_HOST");
        final var locationPort = envOrInputFactory.getInt("LOCATION_PORT");

        locationMessenger = MessengerFactory.udp(locationHost, locationPort);
    }

    @SneakyThrows
    private void register() {
        log.info("Registrando-se no servidor de localização...");
        final var request = new Message(MessageType.REGISTER_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.EDGE)
                .withValue(Fields.HOST, InetAddress.getLocalHost().getHostAddress())
                .withValue(Fields.PORT, serverMessenger.getPort())
                .withValue(Fields.PUBLIC_KEY, publicEncryptionKey.getEncoded());

        locationMessenger.send(request);

        final var response = locationMessenger.receive();

        if (response.getType().equals(MessageType.ERROR)) {
            log.error("Erro ao registrar servidor de borda: {}", response.getValues());
        } else {
            log.info("Registrado no servidor de localização: {}");
        }
    }

    @SneakyThrows
    private void locateDatacenterServer() {
        final var request = new Message(MessageType.LOCATE_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.DATACENTER);

        do {
            locationMessenger.send(request);
            final var response = locationMessenger.receive();

            if (response.getType().equals(MessageType.OK)) {
                final var host = (String) response.getValues().get(Fields.HOST);
                final var port = (int) response.getValues().get(Fields.PORT);

                datacenterMessenger = MessengerFactory.secureTcp(host, port, oldCryptoService);
            }

            if (datacenterMessenger == null) {
                Thread.sleep(INTERVAL);
            }
        } while (datacenterMessenger == null);
    }

    private Message useSymmetric(final Message request) {
        if (MessageType.USE_SYMMETRIC.equals(request.getType())) {
            log.info("Nova conexão assimétrica. Preparando-se para usar simétrica.");

            final var encryptionKey = CryptoServiceFactory.generateAESKey();
            final var hmacKey = CryptoServiceFactory.generateAESKey();

            final var cryptoService = CryptoServiceFactory.aes(encryptionKey, hmacKey);
            final var symmetricMessenger = ServerMessengerFactory.secureUdp(cryptoService);
            symmetricMessenger.subscribe(this::handleRequest);
            log.info("Aguardando mensagens simétricas...");

            return MessageFactory.ok()
                    .withValue(Fields.PORT, symmetricMessenger.getPort())
                    .withValue("ENCRYPTION_KEY", encryptionKey.getEncoded())
                    .withValue("HMAC_KEY", hmacKey.getEncoded());
        } else {
            return MessageFactory.error("Tipo de mensagem não suportada");
        }
    }

    private Message handleRequest(final Message request) {
        log.info("Leitura recebida: {}", request.getValues());

        return TokenHandler.handle(tokenService, request, InstanceType.DEVICE, (identifier, rqst) -> {
            request.setType(MessageType.STORE_SNAPSHOT);
            datacenterMessenger.send(request);
            return MessageFactory.ok();
        });
    }

}
