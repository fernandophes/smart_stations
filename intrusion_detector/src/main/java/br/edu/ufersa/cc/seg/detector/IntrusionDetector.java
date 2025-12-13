package br.edu.ufersa.cc.seg.detector;

import java.net.InetAddress;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import br.edu.ufersa.cc.seg.common.auth.TokenService;
import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.factories.CryptoServiceFactory;
import br.edu.ufersa.cc.seg.common.factories.EnvOrInputFactory;
import br.edu.ufersa.cc.seg.common.factories.MessageFactory;
import br.edu.ufersa.cc.seg.common.factories.MessengerFactory;
import br.edu.ufersa.cc.seg.common.factories.ServerMessengerFactory;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.Messenger;
import br.edu.ufersa.cc.seg.common.messengers.ServerMessenger;
import br.edu.ufersa.cc.seg.common.utils.Element;
import br.edu.ufersa.cc.seg.common.utils.Fields;
import br.edu.ufersa.cc.seg.common.utils.InstanceType;
import br.edu.ufersa.cc.seg.common.utils.MessageType;
import br.edu.ufersa.cc.seg.common.utils.ServerType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IntrusionDetector {

    // Dependências
    private final TokenService tokenService;
    private final EnvOrInputFactory envOrInputFactory;

    // Propriedades
    private final PublicKey publicKey;
    private final CryptoService asymmetricCryptoService;
    private final Map<String, AtomicInteger> mistakes = new HashMap<>();

    // Servidores
    private final ServerMessenger serverMessenger;

    // Mensageiros para outras instâncias
    private Messenger locationMessenger;

    public IntrusionDetector(EnvOrInputFactory envOrInputFactory) {
        this.envOrInputFactory = envOrInputFactory;

        final var rsaPair = CryptoServiceFactory.rsaPair();
        this.publicKey = rsaPair.getPublicKey();

        this.asymmetricCryptoService = rsaPair.getPrivateSide();
        this.serverMessenger = ServerMessengerFactory.secureTcp(asymmetricCryptoService);

        final var jwtSecret = envOrInputFactory.getString("JWT_SECRET");
        this.tokenService = new TokenService(jwtSecret);
    }

    public void start() {
        connectToLocationServer();
        register();
        serverMessenger.subscribe(this::serveSymmetric);
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
        insecureMessenger.close();
    }

    @SneakyThrows
    private void register() {
        log.info("Registrando-se no servidor de localização...");
        final var request = new Message(MessageType.REGISTER_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.INTRUSION_DETECTOR_TCP)
                .withValue(Fields.HOST, InetAddress.getLocalHost().getHostAddress())
                .withValue(Fields.PORT, serverMessenger.getPort())
                .withValue(Fields.PUBLIC_KEY, publicKey.getEncoded());

        locationMessenger.send(request);

        final var response = locationMessenger.receive();

        if (response.getType().equals(MessageType.ERROR)) {
            log.error("Erro ao registrar servidor de borda: {}", response.getValues());
        } else {
            log.info("Registrado no servidor de localização: {}");
        }
    }

    @SneakyThrows
    private Message serveSymmetric(final Message request) {
        if (MessageType.USE_SYMMETRIC.equals(request.getType())) {
            log.info("Nova conexão assimétrica. Preparando-se para usar simétrica...");

            final var encryptionKey = CryptoServiceFactory.generateAESKey();
            final var hmacKey = CryptoServiceFactory.generateAESKey();

            final var cryptoService = CryptoServiceFactory.aes(encryptionKey, hmacKey);
            final var symmetricMessenger = ServerMessengerFactory.secureTcp(cryptoService);
            symmetricMessenger.subscribe(this::handleRequest);
            log.info("Aguardando mensagens simétricas...");

            return MessageFactory.ok()
                    .withValue(Fields.HOST, InetAddress.getLocalHost().getHostAddress())
                    .withValue(Fields.PORT, symmetricMessenger.getPort())
                    .withValue(Fields.ENCRYPTION_KEY, encryptionKey.getEncoded())
                    .withValue(Fields.HMAC_KEY, hmacKey.getEncoded());
        } else {
            return MessageFactory.error("Tipo de mensagem não suportada");
        }
    }

    private Message handleRequest(final Message request) {
        log.info("Leitura recebida para análise: {}", request.getValues());

        final String token = request.getValue(Fields.TOKEN);
        final var identifier = tokenService.validateToken(token, InstanceType.DEVICE);

        mistakes.putIfAbsent(identifier, new AtomicInteger(0));

        final String deviceName = request.getValue("deviceName");
        final String formattedTimestamp = request.getValue("timestamp");

        log.info("Analisando as leituras de {} em {}...", deviceName, formattedTimestamp);

        for (final var element : Element.values()) {
            Optional.of((double) request.getValue(element.name()))
                    .ifPresent(value -> {
                        if (value < element.getMin()) {
                            mistakes.get(identifier).incrementAndGet();
                            log.warn("{} apresentou dados suspeitos!", identifier);
                        }
                    });
        }

        return MessageFactory.ok();
    }

}
