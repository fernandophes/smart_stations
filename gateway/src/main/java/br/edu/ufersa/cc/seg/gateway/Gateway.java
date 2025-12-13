package br.edu.ufersa.cc.seg.gateway;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.apache.http.util.EntityUtils;

import com.auth0.jwt.exceptions.JWTVerificationException;

import br.edu.ufersa.cc.seg.FilterFirewall;
import br.edu.ufersa.cc.seg.common.auth.TokenService;
import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.crypto.HybridCryptoException;
import br.edu.ufersa.cc.seg.common.crypto.SecureMessage;
import br.edu.ufersa.cc.seg.common.factories.CryptoServiceFactory;
import br.edu.ufersa.cc.seg.common.factories.EnvOrInputFactory;
import br.edu.ufersa.cc.seg.common.factories.MessageFactory;
import br.edu.ufersa.cc.seg.common.factories.MessengerFactory;
import br.edu.ufersa.cc.seg.common.factories.ServerMessengerFactory;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.Messenger;
import br.edu.ufersa.cc.seg.common.messengers.SecureMessenger;
import br.edu.ufersa.cc.seg.common.messengers.ServerMessenger;
import br.edu.ufersa.cc.seg.common.utils.ConnectionType;
import br.edu.ufersa.cc.seg.common.utils.Constants;
import br.edu.ufersa.cc.seg.common.utils.Fields;
import br.edu.ufersa.cc.seg.common.utils.InstanceType;
import br.edu.ufersa.cc.seg.common.utils.MessageType;
import br.edu.ufersa.cc.seg.common.utils.ServerType;
import br.edu.ufersa.cc.seg.utils.MessengerWithFirewall;
import io.javalin.Javalin;
import io.javalin.http.Context;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Gateway {

    private static final long INTERVAL = 3_000;

    // Dependências
    private final EnvOrInputFactory envOrInputFactory;
    private final TokenService tokenService;

    // Propriedades
    private final PublicKey publicKey;
    private final CryptoService rsaService;
    private final Map<String, CryptoService> httpClients = new HashMap<>();
    private final String intranetHost;
    private final String internetHost;

    // Firewalls
    private final FilterFirewall filterFirewall = new FilterFirewall();

    // Servidores
    private final ServerMessenger tcpServerMessenger;
    private final ServerMessenger udpServerMessenger;
    private final Javalin httpServer;

    // Mensageiros para outras aplicações
    private final String locationIntranetHost;
    private final String locationInternetHost;
    private Messenger locationUdpIntranetMessenger;
    private Messenger locationUdpInternetMessenger;
    private Messenger authTcpMessenger;
    private Messenger edgeUdpMessenger;
    private Messenger intrusionDetectorTcpMessenger;
    private MyHttpClient datacenterHttpClient;

    public Gateway(final EnvOrInputFactory envOrInputFactory) {
        this.envOrInputFactory = envOrInputFactory;

        locationIntranetHost = envOrInputFactory.getString("LOCATION_INTRANET_HOST");
        locationInternetHost = envOrInputFactory.getString("LOCATION_INTERNET_HOST");
        intranetHost = getMatchingHost(locationIntranetHost);
        internetHost = getMatchingHost(locationInternetHost);

        final var rsaPair = CryptoServiceFactory.rsaPair();
        this.publicKey = rsaPair.getPublicKey();

        this.rsaService = rsaPair.getPrivateSide();
        this.tcpServerMessenger = ServerMessengerFactory.secureTcp(rsaService);
        this.udpServerMessenger = ServerMessengerFactory.secureUdp(rsaService);

        httpServer = Javalin.create();

        final var jwtSecret = envOrInputFactory.getString("JWT_SECRET");
        this.tokenService = new TokenService(jwtSecret);
    }

    public void start() {
        // TCP e UDP
        connectToLocationIntranetServer(locationIntranetHost);
        connectToLocationInternetServer(locationInternetHost);
        locateAuthServer();
        locateEdgeServer();
        locateIntrusionDetector();

        // HTTP
        locateDatacenterHttp();
        configureHttpServer(datacenterHttpClient);
        registerHttp();

        // Escrever a regra do Firewall de Filtro de Pacotes
        writeRules();

        // Começar a receber mensagens
        tcpServerMessenger.subscribe(this::serveTcpSymmetric);
        udpServerMessenger.subscribe(this::serveUdpSymmetric);

        // Registrar-se no servidor de localização
        registerTcp();
        registerUdp();
    }

    @SneakyThrows
    public void stop() {
        locationUdpIntranetMessenger.close();
        tcpServerMessenger.close();
        httpServer.stop();
    }

    @SneakyThrows
    private void connectToLocationIntranetServer(final String host) {
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
            locationUdpIntranetMessenger = MessengerFactory.secureUdp(locationHost, locationPort, cryptoService);

            return MessageFactory.ok();
        });

        /*
         * FASE 2
         */
        // Enviar chave pública via conexão insegura (plain text)
        final var insecurePort = envOrInputFactory.getInt("LOCATION_PORT");
        log.info("Conectando com o servidor de localização na intranet via {}:{}...", host, insecurePort);

        final var insecureMessenger = MessengerFactory.udp(host, insecurePort);
        final var insecureRequest = new Message(MessageType.USE_SYMMETRIC)
                .withValue(Fields.HOST, intranetHost)
                .withValue(Fields.PORT, asymmetricMessenger.getPort())
                .withValue(Fields.PUBLIC_KEY, rsaPair.getPublicKey().getEncoded());
        insecureMessenger.send(insecureRequest);
        final var insecureResponse = insecureMessenger.receive();

        log.info("Conexão com Location Server (intranet): \n{}", insecureResponse.toJson());

        /*
         * FASE 4
         */
        asymmetricMessenger.close();
        insecureMessenger.close();
    }

    @SneakyThrows
    private void connectToLocationInternetServer(final String host) {
        log.info("Conectando com o servidor de localização na internet...");

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
            locationUdpInternetMessenger = MessengerFactory.secureUdp(locationHost, locationPort, cryptoService);

            return MessageFactory.ok();
        });

        /*
         * FASE 2
         */
        // Enviar chave pública via conexão insegura (plain text)
        final var insecurePort = envOrInputFactory.getInt("LOCATION_PORT");
        log.info("Conectando com o servidor de localização na internet via {}:{}...", host, insecurePort);

        final var insecureMessenger = MessengerFactory.udp(host, insecurePort);
        final var insecureRequest = new Message(MessageType.USE_SYMMETRIC)
                .withValue(Fields.HOST, internetHost)
                .withValue(Fields.PORT, asymmetricMessenger.getPort())
                .withValue(Fields.PUBLIC_KEY, rsaPair.getPublicKey().getEncoded());

        insecureMessenger.send(insecureRequest);
        final var insecureResponse = insecureMessenger.receive();

        log.info("Conexão com Location Server (internet): \n{}", insecureResponse.toJson());

        /*
         * FASE 4
         */
        asymmetricMessenger.close();
        insecureMessenger.close();
    }

    @SneakyThrows
    private String getMatchingHost(final String remoteHost) {
        final var remoteHostAddress = InetAddress.getByName(remoteHost).getHostAddress();

        final var networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            final var interfaceAddresses = networkInterfaces.nextElement().getInetAddresses();

            while (interfaceAddresses.hasMoreElements()) {
                final var nextElement = interfaceAddresses.nextElement();
                final var ipParts = nextElement.getHostAddress().split("\\.");

                if (ipParts.length >= 2) {
                    final var prefix = String.join(".", ipParts[0], ipParts[1]);

                    if (remoteHostAddress.startsWith(prefix)) {
                        return nextElement.getHostAddress();
                    }
                }
            }
        }

        return InetAddress.getLocalHost().getHostAddress();
    }

    private void configureHttpServer(final MyHttpClient httpClient) {
        httpServer
                .get("/api/snapshots/{starting}", ctx -> {
                    log.info("Requisição HTTP recebida");
                    handleToken(ctx, InstanceType.CLIENT, (identifier, context) -> {
                        // Obter o token
                        final var token = context.header(Fields.TOKEN);

                        try {
                            // Chamar endpoint do datacenter
                            final var timestamp = context.pathParam("starting");
                            final var response = httpClient.getSnapshotsAfter(token, timestamp);

                            // Descriptografar mensagem recebida pelo datacenter
                            final var entityIn = response.getEntity();
                            final var secureJsonIn = EntityUtils.toString(entityIn);
                            final var secureMessageIn = SecureMessage.fromJson(secureJsonIn);

                            final var messageAsBytes = httpClients.get(ServerType.DATACENTER_HTTP.name())
                                    .decrypt(secureMessageIn);

                            // Criptografar para reenviar ao cliente
                            final var cryptoServiceOut = httpClients.get(identifier);
                            final var secureMessageOut = cryptoServiceOut.encrypt(messageAsBytes);
                            ctx.json(secureMessageOut);
                        } catch (final IOException e) {
                            // Ignorar
                        }
                    });
                })
                .get("api/use-symmetric", ctx -> {
                    log.info("Novo cliente");
                    handleToken(ctx, InstanceType.CLIENT, (identifier, context) -> {
                        // Instanciar serviço de criptografia
                        final var encryptionKey = CryptoServiceFactory.generateAESKey();
                        final var hmacKey = CryptoServiceFactory.generateAESKey();
                        final var aesService = CryptoServiceFactory.aes(encryptionKey, hmacKey);

                        // Guardar
                        httpClients.put(identifier, aesService);

                        // Repassar para o cliente
                        final var response = MessageFactory.ok()
                                .withValue(Fields.ENCRYPTION_KEY, encryptionKey.getEncoded())
                                .withValue(Fields.HMAC_KEY, hmacKey.getEncoded());
                        final var secureResponse = rsaService.encrypt(response.toBytes());
                        ctx.json(secureResponse);
                    });
                })
                .start(0);

        log.info("Servidor HTTP iniciado na porta {}", httpServer.port());
    }

    @SneakyThrows
    private void registerTcp() {
        log.info("Registrando-se no servidor de localização (TCP)...");
        final var request = new Message(MessageType.REGISTER_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.GATEWAY_TCP)
                .withValue(Fields.HOST, internetHost)
                .withValue(Fields.PORT, tcpServerMessenger.getPort())
                .withValue(Fields.PUBLIC_KEY, publicKey.getEncoded());

        locationUdpInternetMessenger.send(request);

        final var response = locationUdpInternetMessenger.receive();

        if (response.getType().equals(MessageType.ERROR)) {
            log.error("Erro ao registrar gateway/TCP: {}", response.getValues());
        } else {
            log.info("TCP registrado no servidor de localização");
        }
    }

    @SneakyThrows
    private void registerUdp() {
        log.info("Registrando-se no servidor de localização (UDP)...");
        final var request = new Message(MessageType.REGISTER_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.GATEWAY_UDP)
                .withValue(Fields.HOST, internetHost)
                .withValue(Fields.PORT, udpServerMessenger.getPort())
                .withValue(Fields.PUBLIC_KEY, publicKey.getEncoded());

        locationUdpInternetMessenger.send(request);

        final var response = locationUdpInternetMessenger.receive();

        if (response.getType().equals(MessageType.ERROR)) {
            log.error("Erro ao registrar gateway/UDP: {}", response.getValues());
        } else {
            log.info("UDP registrado no servidor de localização");
        }
    }

    @SneakyThrows
    private void registerHttp() {
        log.info("Registrando-se no servidor de localização...");
        final var request = new Message(MessageType.REGISTER_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.GATEWAY_HTTP)
                .withValue(Fields.HOST, internetHost)
                .withValue(Fields.PORT, httpServer.port())
                .withValue(Fields.PUBLIC_KEY, publicKey.getEncoded());

        locationUdpInternetMessenger.send(request);

        final var response = locationUdpInternetMessenger.receive();

        if (response.getType().equals(MessageType.ERROR)) {
            log.error("Erro ao registrar gateway HTTP: {}", response.getValues());
        } else {
            log.info("Registrado no servidor de localização: {}");
        }
    }

    @SneakyThrows
    private Message serveUdpSymmetric(final Message request) {
        if (MessageType.USE_SYMMETRIC.equals(request.getType())) {
            log.info("Nova conexão assimétrica. Preparando-se para usar simétrica...");

            final var encryptionKey = CryptoServiceFactory.generateAESKey();
            final var hmacKey = CryptoServiceFactory.generateAESKey();

            final var cryptoService = CryptoServiceFactory.aes(encryptionKey, hmacKey);
            final var symmetricMessenger = ServerMessengerFactory.secureUdp(cryptoService);
            symmetricMessenger.subscribe(this::handleUdpRequest);
            log.info("Aguardando mensagens simétricas...");

            return MessageFactory.ok()
                    .withValue(Fields.HOST, internetHost)
                    .withValue(Fields.PORT, symmetricMessenger.getPort())
                    .withValue(Fields.ENCRYPTION_KEY, encryptionKey.getEncoded())
                    .withValue(Fields.HMAC_KEY, hmacKey.getEncoded());
        } else {
            return MessageFactory.error(Constants.UNSUPPORTED);
        }
    }

    @SneakyThrows
    private Message serveTcpSymmetric(final Message request) {
        if (MessageType.USE_SYMMETRIC.equals(request.getType())) {
            log.info("Nova conexão assimétrica. Preparando-se para usar simétrica...");

            final var encryptionKey = CryptoServiceFactory.generateAESKey();
            final var hmacKey = CryptoServiceFactory.generateAESKey();

            final var cryptoService = CryptoServiceFactory.aes(encryptionKey, hmacKey);
            final var symmetricMessenger = ServerMessengerFactory.secureTcp(cryptoService);
            symmetricMessenger.subscribe(this::handleTcpRequest);
            log.info("Aguardando mensagens simétricas...");

            return MessageFactory.ok()
                    .withValue(Fields.HOST, internetHost)
                    .withValue(Fields.PORT, symmetricMessenger.getPort())
                    .withValue(Fields.ENCRYPTION_KEY, encryptionKey.getEncoded())
                    .withValue(Fields.HMAC_KEY, hmacKey.getEncoded());
        } else {
            return MessageFactory.error(Constants.UNSUPPORTED);
        }
    }

    @SneakyThrows
    private void locateAuthServer() {
        final var locateRequest = new Message(MessageType.LOCATE_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.AUTH_TCP);

        do {
            locationUdpIntranetMessenger.send(locateRequest);
            final var locateResponse = locationUdpIntranetMessenger.receive();

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

                authTcpMessenger = new MessengerWithFirewall(aesMessenger, filterFirewall);
            }

            if (authTcpMessenger == null) {
                Thread.sleep(INTERVAL);
            }
        } while (authTcpMessenger == null);
    }

    @SneakyThrows
    private void locateEdgeServer() {
        final var request = new Message(MessageType.LOCATE_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.EDGE_UDP);

        do {
            locationUdpIntranetMessenger.send(request);
            final var response = locationUdpIntranetMessenger.receive();

            if (response.getType().equals(MessageType.OK)) {
                edgeUdpMessenger = new MessengerWithFirewall(connectToEdgeServer(response), filterFirewall);
            }

            if (edgeUdpMessenger == null) {
                Thread.sleep(INTERVAL);
            }
        } while (edgeUdpMessenger == null);
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
        final String aesHost = response.getValue(Fields.HOST);
        final int aesPort = response.getValue(Fields.PORT);
        final String encryptionKey = response.getValue(Fields.ENCRYPTION_KEY);
        final String hmacKey = response.getValue(Fields.HMAC_KEY);
        final var edgeAesService = CryptoServiceFactory.aes(encryptionKey, hmacKey);
        final var aesMessenger = MessengerFactory.secureUdp(aesHost, aesPort, edgeAesService);

        rsaMessenger.close();
        return aesMessenger;
    }

    @SneakyThrows
    private void locateIntrusionDetector() {
        final var request = new Message(MessageType.LOCATE_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.INTRUSION_DETECTOR_TCP);

        do {
            locationUdpIntranetMessenger.send(request);
            final var response = locationUdpIntranetMessenger.receive();

            if (response.getType().equals(MessageType.OK)) {
                intrusionDetectorTcpMessenger = new MessengerWithFirewall(connectToIntrusionDetector(response), filterFirewall);
            }

            if (intrusionDetectorTcpMessenger == null) {
                Thread.sleep(INTERVAL);
            }
        } while (intrusionDetectorTcpMessenger == null);
    }

    @SneakyThrows
    private SecureMessenger connectToIntrusionDetector(final Message locationResponse) {
        // Abrir messenger RSA temporário
        final String rsaHost = locationResponse.getValue(Fields.HOST);
        final int rsaPort = locationResponse.getValue(Fields.PORT);
        final String rsaPublicKey = locationResponse.getValue(Fields.PUBLIC_KEY);
        final var detectorRsaService = CryptoServiceFactory.publicRsa(rsaPublicKey);
        final var rsaMessenger = MessengerFactory.secureTcp(rsaHost, rsaPort, detectorRsaService);

        // Solicitar chave AES
        final var request = new Message(MessageType.USE_SYMMETRIC);
        rsaMessenger.send(request);
        final var response = rsaMessenger.receive();

        // Abrir messenger AES permanente
        final String aesHost = response.getValue(Fields.HOST);
        final int aesPort = response.getValue(Fields.PORT);
        final String encryptionKey = response.getValue(Fields.ENCRYPTION_KEY);
        final String hmacKey = response.getValue(Fields.HMAC_KEY);
        final var detectorAesService = CryptoServiceFactory.aes(encryptionKey, hmacKey);
        final var aesMessenger = MessengerFactory.secureTcp(aesHost, aesPort, detectorAesService);

        rsaMessenger.close();
        return aesMessenger;
    }

    @SneakyThrows
    private void locateDatacenterHttp() {
        log.info("Localizando Datacenter...");

        final var request = new Message(MessageType.LOCATE_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.DATACENTER_HTTP);

        locationUdpIntranetMessenger.send(request);
        final var response = locationUdpIntranetMessenger.receive();

        if (response.getType().equals(MessageType.OK)) {
            log.info("Datacenter localizado! Contatando com criptografia assimétrica...");
            connectToDatacenterHttp(response);
            log.info("Recebidos dados para criptografia simétrica. Conexão atualizada.");
        } else {
            final var msg = "Não foi possível estabelecer a criptografia híbrida";
            log.error(msg);
            throw new HybridCryptoException(msg);
        }
    }

    @SneakyThrows
    private void connectToDatacenterHttp(final Message locationResponse) {
        // Abrir client HTTP
        final String host = locationResponse.getValue(Fields.HOST);
        final int port = locationResponse.getValue(Fields.PORT);
        filterFirewall.permit(ConnectionType.HTTP, InetAddress.getByName(host), port);
        datacenterHttpClient = new MyHttpClient(host, port, filterFirewall);

        // Configurar cifragem assimétrica
        final String httpPublicKey = locationResponse.getValue(Fields.PUBLIC_KEY);
        final var asymmetricCryptoService = CryptoServiceFactory.publicRsa(httpPublicKey);

        // Obter resposta do HTTP
        final var response = datacenterHttpClient.acceptGateway();
        final var entity = response.getEntity();
        final var json = EntityUtils.toString(entity);
        final var secureMessage = SecureMessage.fromJson(json);
        final var messageAsBytes = asymmetricCryptoService.decrypt(secureMessage);
        final var message = Message.fromBytes(messageAsBytes);

        if (MessageType.OK.equals(message.getType())) {
            // Configurar cifragem simétrica
            final String encryptionKey = message.getValue(Fields.ENCRYPTION_KEY);
            final String hmacKey = message.getValue(Fields.HMAC_KEY);
            final var datacenterHttpCryptoService = CryptoServiceFactory.aes(encryptionKey, hmacKey);
            httpClients.put(ServerType.DATACENTER_HTTP.name(), datacenterHttpCryptoService);
        } else {
            throw new HybridCryptoException(message.getValue("message"));
        }
    }

    private void writeRules() {
        filterFirewall.permit(authTcpMessenger)
                .permit(edgeUdpMessenger)
                .permit(intrusionDetectorTcpMessenger)
                .permit(ConnectionType.HTTP, datacenterHttpClient.getHost(), datacenterHttpClient.getPort())
                .printRules();
    }

    @SneakyThrows
    private Message handleUdpRequest(final Message request) {
        if (MessageType.SEND_SNAPSHOT.equals(request.getType())) {
            edgeUdpMessenger.send(request);
            return edgeUdpMessenger.receive();
        } else {
            return MessageFactory.error(Constants.UNSUPPORTED);
        }
    }

    @SneakyThrows
    private Message handleTcpRequest(final Message request) {
        if (MessageType.AUTHENTICATE.equals(request.getType())) {
            authTcpMessenger.send(request);
            return authTcpMessenger.receive();
        } else {
            return MessageFactory.error(Constants.UNSUPPORTED);
        }
    }

    private void handleToken(final Context context, final InstanceType instanceType,
            final BiConsumer<String, Context> callback) {
        Optional.ofNullable(context.header(Fields.TOKEN))
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
