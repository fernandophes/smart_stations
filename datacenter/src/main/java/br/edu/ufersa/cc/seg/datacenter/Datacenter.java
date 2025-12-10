package br.edu.ufersa.cc.seg.datacenter;

import java.io.IOException;
import java.net.InetAddress;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import com.auth0.jwt.exceptions.JWTVerificationException;

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
import br.edu.ufersa.cc.seg.common.utils.Constants;
import br.edu.ufersa.cc.seg.common.utils.Element;
import br.edu.ufersa.cc.seg.common.utils.Fields;
import br.edu.ufersa.cc.seg.common.utils.InstanceType;
import br.edu.ufersa.cc.seg.common.utils.MessageType;
import br.edu.ufersa.cc.seg.common.utils.ServerType;
import br.edu.ufersa.cc.seg.datacenter.entities.Snapshot;
import br.edu.ufersa.cc.seg.datacenter.services.SnapshotService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Datacenter {

    private final Map<String, CryptoService> clients = new HashMap<>();

    private final TokenService tokenService;
    private final CryptoService asymmetricCryptoService;
    private final EnvOrInputFactory envOrInputFactory;

    private final PublicKey publicKey;

    private final Javalin httpServer;
    private final ServerMessenger serverMessenger;
    private Messenger locationMessenger;

    /*
     * Serviço do banco de dados
     */
    private final SnapshotService snapshotService = new SnapshotService();

    public Datacenter(final EnvOrInputFactory envOrInputFactory)
            throws IOException {
        this.envOrInputFactory = envOrInputFactory;

        final var rsaPair = CryptoServiceFactory.rsaPair();
        this.publicKey = rsaPair.getPublicKey();

        this.asymmetricCryptoService = rsaPair.getPrivateSide();
        this.serverMessenger = ServerMessengerFactory.secureUdp(asymmetricCryptoService);

        httpServer = Javalin.create();

        final var jwtSecret = envOrInputFactory.getString("JWT_SECRET");
        this.tokenService = new TokenService(jwtSecret);
    }

    public void start() {
        connectToLocationServer();
        configureHttpServer();
        register();
        registerHttp();
        serverMessenger.subscribe(this::serveSymmetric);
    }

    @SneakyThrows
    public void stop() {
        locationMessenger.close();
        serverMessenger.close();
        httpServer.stop();
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
                .withValue(Fields.SERVER_TYPE, ServerType.DATACENTER)
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

    @SneakyThrows
    private void registerHttp() {
        log.info("Registrando-se no servidor de localização...");
        final var request = new Message(MessageType.REGISTER_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.HTTP)
                .withValue(Fields.HOST, InetAddress.getLocalHost().getHostAddress())
                .withValue(Fields.PORT, httpServer.port())
                .withValue(Fields.PUBLIC_KEY, publicKey.getEncoded());

        locationMessenger.send(request);

        final var response = locationMessenger.receive();

        if (response.getType().equals(MessageType.ERROR)) {
            log.error("Erro ao registrar datacenter: {}", response.getValues());
        } else {
            log.info("Registrado no servidor de localização: {}");
        }
    }

    private void configureHttpServer() {
        httpServer
                .get("/api/snapshots", ctx -> {
                    log.info("Requisição HTTP recebida");
                    handleToken(tokenService, ctx, InstanceType.CLIENT,
                            (identifier, context) -> {
                                final var message = MessageFactory.ok("data", snapshotService.listAll());
                                final var encMessage = clients.get(identifier).encrypt(message.toBytes());
                                context.json(encMessage);
                            });
                })
                .get("/api/snapshots/{starting}", ctx -> {
                    log.info("Requisição HTTP recebida");
                    handleToken(tokenService, ctx, InstanceType.CLIENT, (identifier, context) -> {
                        final var formattedTimestamp = context.pathParam("starting");
                        final var timestamp = LocalDateTime.parse(formattedTimestamp,
                                Constants.DATE_TIME_URL_FORMATTER);
                        final var message = MessageFactory.ok("data", snapshotService.listAllAfter(timestamp));
                        final var encMessage = clients.get(identifier).encrypt(message.toBytes());
                        context.json(encMessage);
                    });
                })
                .get("api/use-symmetric", ctx -> {
                    log.info("Novo cliente");
                    handleToken(tokenService, ctx, InstanceType.CLIENT, (identifier, context) -> {
                        final var encryptionKey = CryptoServiceFactory.generateAESKey();
                        final var hmacKey = CryptoServiceFactory.generateAESKey();

                        final var cryptoService = CryptoServiceFactory.aes(encryptionKey, hmacKey);
                        clients.put(identifier, cryptoService);

                        final var response = MessageFactory.ok()
                                .withValue(Fields.ENCRYPTION_KEY, encryptionKey.getEncoded())
                                .withValue(Fields.HMAC_KEY, hmacKey.getEncoded());
                        final var asymmetricEncryptedResponse = asymmetricCryptoService.encrypt(response.toBytes());
                        context.json(asymmetricEncryptedResponse);
                    });
                })
                .start(0);

        log.info("Servidor HTTP iniciado na porta {}", httpServer.port());
    }

    private Message serveSymmetric(final Message request) {
        if (MessageType.USE_SYMMETRIC.equals(request.getType())) {
            log.info("Nova conexão assimétrica. Preparando-se para usar simétrica...");

            final var encryptionKey = CryptoServiceFactory.generateAESKey();
            final var hmacKey = CryptoServiceFactory.generateAESKey();

            final var cryptoService = CryptoServiceFactory.aes(encryptionKey, hmacKey);
            final var symmetricMessenger = ServerMessengerFactory.secureUdp(cryptoService);
            symmetricMessenger.subscribe(this::handleRequest);
            log.info("Aguardando mensagens simétricas...");

            return MessageFactory.ok()
                    .withValue(Fields.PORT, symmetricMessenger.getPort())
                    .withValue(Fields.ENCRYPTION_KEY, encryptionKey.getEncoded())
                    .withValue(Fields.HMAC_KEY, hmacKey.getEncoded());
        } else {
            return MessageFactory.error("Tipo de mensagem não suportada");
        }
    }

    private Message handleRequest(final Message request) {
        if (MessageType.STORE_SNAPSHOT.equals(request.getType())) {
            storeSnapshot(request);
            return MessageFactory.ok();
        } else {
            return MessageFactory.error("Tipo de mensagem não suportada");
        }

    }

    private void handleToken(final TokenService tokenService, final Context context, final InstanceType instanceType,
            final BiConsumer<String, Context> callback) {
        Optional.ofNullable(context.header("token"))
                .flatMap(tkn -> {
                    try {
                        final var identifier = tokenService.validateToken(tkn, instanceType);
                        return Optional.of(identifier);
                    } catch (final JWTVerificationException e) {
                        return Optional.empty();
                    }
                })
                .ifPresentOrElse(identifier -> callback.accept(identifier, context),
                        () -> {
                            final var response = MessageFactory.error("Token invalido");
                            final var encResponse = asymmetricCryptoService.encrypt(response.toBytes());
                            context.status(401).json(encResponse);
                        });
    }

    private void storeSnapshot(final Message request) {
        final var values = request.getValues();
        final var deviceName = (String) values.get("deviceName");
        final var formattedTimestamp = (String) values.get("timestamp");
        final var timestamp = LocalDateTime.parse(formattedTimestamp, Constants.DATE_TIME_FORMATTER);

        log.info("Armazenando as leituras de {} em {}...", deviceName, formattedTimestamp);

        for (final var element : Element.values()) {
            Optional.of((double) values.get(element.name()))
                    .ifPresent(value -> {
                        final var snapshot = new Snapshot()
                                .setDeviceName(deviceName)
                                .setTimestamp(timestamp)
                                .setElement(element)
                                .setCapturedValue(value);

                        snapshotService.create(snapshot);
                    });
        }

        log.info("Leituras de {} em {} armazenadas", deviceName, formattedTimestamp);
    }

}
