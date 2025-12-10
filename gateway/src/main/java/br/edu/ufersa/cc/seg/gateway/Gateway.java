package br.edu.ufersa.cc.seg.gateway;

import java.io.IOException;
import java.net.InetAddress;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.apache.http.util.EntityUtils;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.edu.ufersa.cc.seg.common.auth.TokenService;
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
import br.edu.ufersa.cc.seg.common.messengers.SecureMessenger;
import br.edu.ufersa.cc.seg.common.messengers.ServerMessenger;
import br.edu.ufersa.cc.seg.common.utils.Fields;
import br.edu.ufersa.cc.seg.common.utils.InstanceType;
import br.edu.ufersa.cc.seg.common.utils.MessageType;
import br.edu.ufersa.cc.seg.common.utils.ServerType;
import io.javalin.Javalin;
import io.javalin.http.Context;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Gateway {

    private static final long INTERVAL = 3_000;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Dependências
    private final EnvOrInputFactory envOrInputFactory;
    private final TokenService tokenService;

    // Propriedades
    private final PublicKey publicKey;
    private final CryptoService rsaService;
    private final Map<String, CryptoService> httpClients = new HashMap<>();

    // Servidores
    private final ServerMessenger serverMessenger;
    private final Javalin httpServer;

    // Mensageiros para outras aplicações
    private Messenger locationMessenger;
    private Messenger edgeMessenger;
    private MyHttpClient datacenterHttpClient;
    private CryptoService datacenterHttpCryptoService;

    public Gateway(final EnvOrInputFactory envOrInputFactory) {
        this.envOrInputFactory = envOrInputFactory;

        final var rsaPair = CryptoServiceFactory.rsaPair();
        this.publicKey = rsaPair.getPublicKey();

        this.rsaService = rsaPair.getPrivateSide();
        this.serverMessenger = ServerMessengerFactory.secureTcp(rsaService);

        httpServer = Javalin.create();

        final var jwtSecret = envOrInputFactory.getString("JWT_SECRET");
        this.tokenService = new TokenService(jwtSecret);
    }

    public void start() {
        connectToLocationServer();
        configureHttpServer();
        locateEdgeServer();
        locateDatacenterHttp(null);
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

    private void configureHttpServer() {
        httpServer
                .get("/api/snapshots", ctx -> {
                    log.info("Requisição HTTP recebida");
                    handleToken(ctx, InstanceType.CLIENT, (identifier, context) -> {
                        // TODO
                    });
                })
                .get("/api/snapshots/{starting}", ctx -> {
                    log.info("Requisição HTTP recebida");
                    handleToken(ctx, InstanceType.CLIENT, (identifier, context) -> {
                        final var token = context.header("token");
                        final var response = datacenterHttpClient.getSnapshotsAfter(token, identifier);

                        try {
                            // Descriptografar mensagem recebida pelo datacenter
                            final var entityIn = response.getEntity();
                            final var secureJsonIn = EntityUtils.toString(entityIn);
                            final var secureMessageIn = SecureMessage.fromJson(secureJsonIn);

                            final var messageAsBytes = datacenterHttpCryptoService.decrypt(secureMessageIn);

                            // Criptografar para reenviar ao cliente
                            final var cryptoServiceOut = httpClients.get(identifier);
                            final var secureMessageOut = cryptoServiceOut.encrypt(messageAsBytes);
                            ctx.json(secureMessageOut);
                        } catch (IOException e) {
                            // Ignorar
                        }
                    });
                })
                .get("api/use-symmetric", ctx -> {
                    log.info("Novo cliente");
                    handleToken(ctx, InstanceType.CLIENT, (identifier, context) -> {
                        // TODO
                    });
                })
                .start(0);

        log.info("Servidor HTTP iniciado na porta {}", httpServer.port());
    }

    @SneakyThrows
    private void register() {
        log.info("Registrando-se no servidor de localização...");
        final var request = new Message(MessageType.REGISTER_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.GATEWAY)
                .withValue(Fields.HOST, InetAddress.getLocalHost().getHostAddress())
                .withValue(Fields.PORT, serverMessenger.getPort())
                .withValue(Fields.PUBLIC_KEY, publicKey.getEncoded());

        locationMessenger.send(request);

        final var response = locationMessenger.receive();

        if (response.getType().equals(MessageType.ERROR)) {
            log.error("Erro ao registrar gateway: {}", response.getValues());
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
            log.error("Erro ao registrar gateway HTTP: {}", response.getValues());
        } else {
            log.info("Registrado no servidor de localização: {}");
        }
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

    @SneakyThrows
    private void locateEdgeServer() {
        final var request = new Message(MessageType.LOCATE_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.EDGE);

        do {
            locationMessenger.send(request);
            final var response = locationMessenger.receive();

            if (response.getType().equals(MessageType.OK)) {
                edgeMessenger = connectToEdgeServer(response);
            }

            if (edgeMessenger == null) {
                Thread.sleep(INTERVAL);
            }
        } while (edgeMessenger == null);
    }

    @SneakyThrows
    private SecureMessenger connectToEdgeServer(final Message locationResponse) {
        // Abrir messenger RSA temporário
        final String rsaHost = locationResponse.getValue(Fields.HOST);
        final int rsaPort = locationResponse.getValue(Fields.PORT);
        final String rsaPublicKey = locationResponse.getValue(Fields.PUBLIC_KEY);
        final var edgeRsaService = CryptoServiceFactory.publicRsa(rsaPublicKey);
        final var rsaMessenger = MessengerFactory.secureUdp(rsaHost, rsaPort, edgeRsaService);

        // Solicitar chave AES
        final var request = new Message(MessageType.USE_SYMMETRIC);
        rsaMessenger.send(request);
        final var response = rsaMessenger.receive();

        // Abrir messenger AES permanente
        final int aesPort = response.getValue(Fields.PORT);
        final String encryptionKey = response.getValue(Fields.ENCRYPTION_KEY);
        final String hmacKey = response.getValue(Fields.HMAC_KEY);
        final var edgeAesService = CryptoServiceFactory.aes(encryptionKey, hmacKey);
        final var aesMessenger = MessengerFactory.secureUdp(rsaHost, aesPort, edgeAesService);

        rsaMessenger.close();
        return aesMessenger;
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
        // Abrir client HTTP
        final String host = locationResponse.getValue(Fields.HOST);
        final int port = locationResponse.getValue(Fields.PORT);
        datacenterHttpClient = new MyHttpClient(host, port);

        // Configurar cifragem assimétrica
        final String httpPublicKey = locationResponse.getValue(Fields.PUBLIC_KEY);
        final var asymmetricCryptoService = CryptoServiceFactory.publicRsa(httpPublicKey);

        // Obter resposta do HTTP
        final var response = datacenterHttpClient.useSymmetric(token);
        final var entity = response.getEntity();
        final var json = EntityUtils.toString(entity);
        final var secureMessage = SecureMessage.fromJson(json);
        final var messageAsBytes = asymmetricCryptoService.decrypt(secureMessage);
        final var message = Message.fromBytes(messageAsBytes);

        if (MessageType.OK.equals(message.getType())) {
            // Configurar cifragem simétrica
            final String encryptionKey = message.getValue(Fields.ENCRYPTION_KEY);
            final String hmacKey = message.getValue(Fields.HMAC_KEY);
            datacenterHttpCryptoService = CryptoServiceFactory.aes(encryptionKey, hmacKey);
        } else {
            throw new HybridCryptoException(message.getValue("message"));
        }
    }

    @SneakyThrows
    private Message handleRequest(final Message request) {
        if (MessageType.STORE_SNAPSHOT.equals(request.getType())) {
            edgeMessenger.send(request);
            return edgeMessenger.receive();
        } else {
            return MessageFactory.error("Tipo de mensagem não suportada");
        }
    }

    private void handleToken(final Context context, final InstanceType instanceType,
            final BiConsumer<String, Context> callback) {
        Optional.ofNullable(context.header("token"))
                .flatMap(token -> {
                    try {
                        final var identifier = tokenService.validateToken(token, instanceType);
                        return Optional.of(identifier);
                    } catch (final JWTVerificationException e) {
                        return Optional.empty();
                    }
                })
                .ifPresentOrElse(identifier -> callback.accept(identifier, context),
                        () -> {
                            final var response = MessageFactory.error("Token invalido");
                            final var encResponse = rsaService.encrypt(response.toBytes());
                            context.status(401).json(encResponse);
                        });
    }

}
