package br.edu.ufersa.cc.seg.location;

import java.io.IOException;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import br.edu.ufersa.cc.seg.common.factories.MessageFactory;
import br.edu.ufersa.cc.seg.common.factories.ServerMessengerFactory;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.ServerMessenger;
import br.edu.ufersa.cc.seg.common.messengers.Messenger.Subscription;
import br.edu.ufersa.cc.seg.common.utils.Fields;
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
        PublicKey publicEncriptionKey;
        PublicKey publicHmacKey;
    }

    private final Map<ServerType, Location> locations = new HashMap<>();
    private final ServerMessenger serverMessenger;
    private Subscription subscription;

    public LocationServer(final int port) throws IOException {
        this.serverMessenger = ServerMessengerFactory.udp(port);
    }

    public void start() {
        serverMessenger.subscribe(this::handleRequest);
    }

    public void register(final ServerType serverType, final String host, final int port,
            final PublicKey publicEncriptionKey, final PublicKey publicHmacKey) {
        log.info("Registrando {} na localização {}:{} e sua chave pública {}", serverType, host, port,
                publicEncriptionKey);
        locations.put(serverType, new Location(host, port, publicEncriptionKey, publicHmacKey));
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

    private Message handleRequest(final Message request) {
        switch (request.getType()) {
            case REGISTER_SERVER: {
                final var serverType = ServerType.valueOf((String) request.getValues().get(Fields.SERVER_TYPE));
                final var host = (String) request.getValues().get(Fields.HOST);
                final var port = (int) request.getValues().get(Fields.PORT);
                final var publicEncriptionKey = (PublicKey) request.getValues().get(Fields.PUBLIC_ENCRIPTION_KEY);
                final var publicHmacKey = (PublicKey) request.getValues().get(Fields.PUBLIC_HMAC_KEY);
                register(serverType, host, port, publicEncriptionKey, publicHmacKey);
                return MessageFactory.ok();
            }

            case LOCATE_SERVER: {
                final var serverType = ServerType.valueOf((String) request.getValues().get(Fields.SERVER_TYPE));
                return locate(serverType)
                        .map(location -> MessageFactory.ok()
                                .withValue(Fields.HOST, location.getHost())
                                .withValue(Fields.PORT, location.getPort())
                                .withValue(Fields.PUBLIC_ENCRIPTION_KEY, location.getPublicEncriptionKey())
                                .withValue(Fields.PUBLIC_HMAC_KEY, location.getPublicHmacKey()))
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

}
