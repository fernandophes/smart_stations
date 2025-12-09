package br.edu.ufersa.cc.seg.location;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import br.edu.ufersa.cc.seg.common.factories.CryptoServiceFactory;
import br.edu.ufersa.cc.seg.common.factories.MessageFactory;
import br.edu.ufersa.cc.seg.common.factories.MessengerFactory;
import br.edu.ufersa.cc.seg.common.factories.ServerMessengerFactory;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.Messenger.Subscription;
import br.edu.ufersa.cc.seg.common.messengers.ServerMessenger;
import br.edu.ufersa.cc.seg.common.utils.Fields;
import br.edu.ufersa.cc.seg.common.utils.MessageType;
import br.edu.ufersa.cc.seg.common.utils.ServerType;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocationServer {

    @Value
    private class Location {
        String host;
        int port;
        String publicKey;
    }

    private final Map<ServerType, Location> locations = new HashMap<>();
    private final ServerMessenger serverMessenger;
    private Subscription subscription;

    public LocationServer(final int port) throws IOException {
        this.serverMessenger = ServerMessengerFactory.udp(port);
    }

    public void start() {
        serverMessenger.subscribe(this::handleFirstContact);
    }

    public void register(final ServerType serverType, final String host, final int port,
            final String publicEncriptionKey) {
        log.info("Registrando {} na localização {}:{} e sua chave pública {}", serverType, host, port,
                publicEncriptionKey);
        locations.put(serverType, new Location(host, port, publicEncriptionKey));
        logLocations();
    }

    public Optional<Location> locate(final ServerType serverType) {
        log.info("Obtendo {}...", serverType);
        return Optional.ofNullable(locations.get(serverType));
    }

    public void remove(final ServerType serverType) {
        locations.remove(serverType);
    }

    @SneakyThrows
    public void stop() {
        subscription.close();
        serverMessenger.close();
        subscription = null;
    }

    @SneakyThrows
    private Message handleFirstContact(final Message request) {
        // Ignorar mensagens de outros tipos
        if (!MessageType.USE_SYMMETRIC.equals(request.getType())) {
            return MessageFactory.error("Tipo de mensagem não suportada");
        }

        /*
         * FASE 1
         */
        log.info("Nova conexão insegura. Preparando-se para usar assimétrica...");

        // Obter dados de conexão assimétrica da instância, via conexão insegura
        final String instancePublicKey = request.getValue(Fields.PUBLIC_KEY);
        final String instanceHost = request.getValue(Fields.HOST);
        final int instancePort = request.getValue(Fields.PORT);

        // Abrir messenger RSA temporário só para enviar as chaves AES mesmo
        final var instanceCryptoService = CryptoServiceFactory.publicRsa(instancePublicKey);
        final var asymmetricMessenger = MessengerFactory.secureUdp(instanceHost, instancePort, instanceCryptoService);

        /*
         * FASE 2
         */
        log.info("Nova conexão assimétrica. Preparando-se para usar simétrica...");

        // Abrir servidor AES permanente pra futuras comunicações
        final var encryptionKey = CryptoServiceFactory.generateAESKey();
        final var hmacKey = CryptoServiceFactory.generateAESKey();

        final var cryptoService = CryptoServiceFactory.aes(encryptionKey, hmacKey);
        final var symmetricMessenger = ServerMessengerFactory.secureUdp(cryptoService);
        symmetricMessenger.subscribe(this::handleRequest);
        log.info("Aguardando mensagens simétricas...");

        // Enviar chaves AES pelo messenger assimétrico
        final var aesData = new Message(MessageType.USE_SYMMETRIC)
                .withValue(Fields.HOST, InetAddress.getLocalHost().getHostAddress())
                .withValue(Fields.PORT, symmetricMessenger.getPort())
                .withValue(Fields.ENCRYPTION_KEY, encryptionKey.getEncoded())
                .withValue(Fields.HMAC_KEY, hmacKey.getEncoded());

        asymmetricMessenger.send(aesData);
        final var response = asymmetricMessenger.receive();
        log.info("Resposta da instância ao dados do AES:\n{}", response);

        /*
         * FASE 3
         */

        // Fechar messenger RSA temporário
        log.info("Fechando conexão assimétrica...");
        asymmetricMessenger.close();

        // Finalizar conexão insegura
        return MessageFactory.ok();
    }

    private Message handleRequest(final Message request) {
        switch (request.getType()) {
            case REGISTER_SERVER: {
                final var serverType = ServerType.valueOf((String) request.getValues().get(Fields.SERVER_TYPE));
                final var host = (String) request.getValues().get(Fields.HOST);
                final var port = (int) request.getValues().get(Fields.PORT);
                final var publicKey = (String) request.getValues().get(Fields.PUBLIC_KEY);
                register(serverType, host, port, publicKey);
                return MessageFactory.ok();
            }

            case LOCATE_SERVER: {
                final var serverType = ServerType.valueOf((String) request.getValues().get(Fields.SERVER_TYPE));
                return locate(serverType)
                        .map(location -> MessageFactory.ok()
                                .withValue(Fields.HOST, location.getHost())
                                .withValue(Fields.PORT, location.getPort())
                                .withValue(Fields.PUBLIC_KEY, location.getPublicKey()))
                        .orElseGet(() -> MessageFactory.error("Servidor não localizado"));
            }

            case REMOVE_SERVER: {
                final var serverType = ServerType.valueOf((String) request.getValues().get(Fields.SERVER_TYPE));
                remove(serverType);
                return MessageFactory.ok();
            }

            default: {
                final var msg = "Mensagem do tipo {} não é suportada";

                log.error(msg);
                return MessageFactory.error(msg);
            }
        }
    }

    private void logLocations() {
        final var builder = new StringBuilder("MAPA DE LOCALIZAÇÕES:\n");
        final var template = """

                Tipo: %s
                Localização: %s:%d
                Chave pública: %s
                            """;

        locations.forEach((key, value) -> {
            final var info = String.format(template, key, value.getHost(), value.getPort(), value.getPublicKey());
            builder.append(info);
        });

        log.info(builder.toString());
    }

}
