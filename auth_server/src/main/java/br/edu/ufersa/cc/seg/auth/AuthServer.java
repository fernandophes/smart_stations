package br.edu.ufersa.cc.seg.auth;

import java.net.InetAddress;
import java.security.PublicKey;

import br.edu.ufersa.cc.seg.auth.dto.InstanceDto;
import br.edu.ufersa.cc.seg.auth.exceptions.AuthFailureException;
import br.edu.ufersa.cc.seg.auth.services.AuthService;
import br.edu.ufersa.cc.seg.auth.services.InstanceService;
import br.edu.ufersa.cc.seg.common.factories.CryptoServiceFactory;
import br.edu.ufersa.cc.seg.common.factories.EnvOrInputFactory;
import br.edu.ufersa.cc.seg.common.factories.MessageFactory;
import br.edu.ufersa.cc.seg.common.factories.MessengerFactory;
import br.edu.ufersa.cc.seg.common.factories.ServerMessengerFactory;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.Messenger;
import br.edu.ufersa.cc.seg.common.messengers.ServerMessenger;
import br.edu.ufersa.cc.seg.common.utils.Fields;
import br.edu.ufersa.cc.seg.common.utils.InstanceType;
import br.edu.ufersa.cc.seg.common.utils.MessageType;
import br.edu.ufersa.cc.seg.common.utils.ServerType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AuthServer {

    private final EnvOrInputFactory envOrInputFactory;

    private final AuthService service;
    private final InstanceService instanceService = new InstanceService();
    private ServerMessenger serverMessenger;
    private Messenger locationMessenger;

    public AuthServer(final EnvOrInputFactory envOrInputFactory) {
        this.envOrInputFactory = envOrInputFactory;

        final var secret = envOrInputFactory.getString("JWT_SECRET");
        service = new AuthService(secret);
    }

    public void start() {
        connectToLocationServer();
        final var publicKey = startRsaServer();
        register(publicKey);
        serverMessenger.subscribe(this::handleRequest);
        seed();
    }

    @SneakyThrows
    public void stop() {
        locationMessenger.close();
        serverMessenger.close();
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

    private PublicKey startRsaServer() {
        final var rsaPair = CryptoServiceFactory.rsaPair();
        serverMessenger = ServerMessengerFactory.secureTcp(rsaPair.getPrivateSide());

        return rsaPair.getPublicKey();
    }

    @SneakyThrows
    private void register(final PublicKey publicKey) {
        log.info("Registrando-se no servidor de localização...");
        final var request = new Message(MessageType.REGISTER_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.AUTH)
                .withValue(Fields.HOST, InetAddress.getLocalHost().getHostAddress())
                .withValue(Fields.PORT, serverMessenger.getPort())
                .withValue(Fields.PUBLIC_KEY, publicKey.getEncoded());

        locationMessenger.send(request);

        final var response = locationMessenger.receive();

        if (response.getType().equals(MessageType.ERROR)) {
            log.error("Erro ao registrar datacenter: {}", response.getValues());
        } else {
            log.info("Registrado no servidor de localização: {}");
        }
    }

    private Message handleRequest(final Message request) {
        if (MessageType.AUTHENTICATE.equals(request.getType())) {
            final var identifier = (String) request.getValues().get("identifier");
            final var secret = (String) request.getValues().get("secret");

            try {
                final var token = service.authenticate(identifier, secret);
                return MessageFactory.ok("token", token);
            } catch (final AuthFailureException e) {
                return MessageFactory.error("Credenciais inválidas");
            }
        } else {
            return MessageFactory.error("Tipo de mensagem não suportada");
        }
    }

    private void seed() {
        final var sensor = new InstanceDto().setType(InstanceType.DEVICE).setIdentifier("sensor-a");
        instanceService.create(sensor, "sensor123");

        final var post = new InstanceDto().setType(InstanceType.DEVICE).setIdentifier("post-a");
        instanceService.create(post, "post123");

        final var semaphor = new InstanceDto().setType(InstanceType.DEVICE).setIdentifier("semaphor-a");
        instanceService.create(semaphor, "semaphor123");

        final var toten = new InstanceDto().setType(InstanceType.DEVICE).setIdentifier("toten-a");
        instanceService.create(toten, "toten123");

        final var browser = new InstanceDto().setType(InstanceType.CLIENT).setIdentifier("browser-a");
        instanceService.create(browser, "browser123");
    }

}
