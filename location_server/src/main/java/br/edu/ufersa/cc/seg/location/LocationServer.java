package br.edu.ufersa.cc.seg.location;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.factories.MessageFactory;
import br.edu.ufersa.cc.seg.common.network.Messenger;
import br.edu.ufersa.cc.seg.common.network.UdpMessenger;
import br.edu.ufersa.cc.seg.common.network.Messenger.Subscription;
import br.edu.ufersa.cc.seg.common.utils.ServerType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocationServer {

    private abstract static class Fields {
        static final String ADDRESS = "address";
        static final String SERVER_TYPE = "serverType";
    }

    private final Map<ServerType, InetAddress> addresses = new HashMap<>();
    private final Messenger messenger;
    private Subscription subscription;

    public LocationServer(final int port, final CryptoService cryptoService) throws IOException {
        this.messenger = new UdpMessenger(port, cryptoService);
    }

    public void start() {
        subscription = messenger.subscribe(request -> {
            switch (request.getType()) {
                case REGISTER_SERVER: {
                    final var serverType = (ServerType) request.getValues().get(Fields.SERVER_TYPE);
                    final var address = (InetAddress) request.getValues().get(Fields.ADDRESS);
                    register(serverType, address);
                    return MessageFactory.ok();
                }

                case LOCATE_SERVER: {
                    final var serverType = (ServerType) request.getValues().get(Fields.SERVER_TYPE);
                    return locate(serverType)
                            .map(foundAddress -> MessageFactory.ok(Fields.ADDRESS, foundAddress))
                            .orElseGet(() -> MessageFactory.error("Servidor não localizado"));
                }

                case REMOVE_SERVER: {
                    final var serverType = (ServerType) request.getValues().get(Fields.SERVER_TYPE);
                    remove(serverType);
                    return MessageFactory.ok();
                }

                default: {
                    final var msg = "Mensagem do tipo {} não é suportada";

                    log.error(msg);
                    return MessageFactory.error(msg);
                }
            }
        });
    }

    public void register(final ServerType serverType, final InetAddress address) {
        addresses.put(serverType, address);
    }

    public Optional<InetAddress> locate(final ServerType serverType) {
        return Optional.ofNullable(addresses.getOrDefault(serverType, null));
    }

    public void remove(final ServerType serverType) {
        addresses.remove(serverType);
    }

    @SneakyThrows
    public void stop() {
        subscription.close();
        subscription = null;
    }

}
