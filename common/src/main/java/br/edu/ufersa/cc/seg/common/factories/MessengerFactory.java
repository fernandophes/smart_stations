package br.edu.ufersa.cc.seg.common.factories;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import br.edu.ufersa.cc.seg.common.concrete_messengers.TcpMessenger;
import br.edu.ufersa.cc.seg.common.concrete_messengers.UdpMessenger;
import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.messengers.Messenger;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class MessengerFactory {

    @Value
    private static class MessengerInfo {
        private final String host;
        private final int port;
    }

    private static final Map<MessengerInfo, TcpMessenger> TCP_MESSENGERS = new HashMap<>();
    private static final Map<MessengerInfo, UdpMessenger> UDP_MESSENGERS = new HashMap<>();

    public static Messenger tcp(final String host, final int port, final CryptoService cryptoService) {
        final var info = new MessengerInfo(host, port);

        return Optional.ofNullable(TCP_MESSENGERS.get(info))
                .orElseGet(() -> {
                    final var messenger = createTcpMessenger(host, port, cryptoService);
                    TCP_MESSENGERS.put(info, messenger);

                    return messenger;
                });
    }

    public static Messenger udp(final String host, final int port, final CryptoService cryptoService) {
        final var info = new MessengerInfo(host, port);

        return Optional.ofNullable(UDP_MESSENGERS.get(info))
                .orElseGet(() -> {
                    final var messenger = createUdpMessenger(host, port, cryptoService);
                    UDP_MESSENGERS.put(info, messenger);

                    return messenger;
                });
    }

    @SneakyThrows
    private static TcpMessenger createTcpMessenger(final String host, final int port,
            final CryptoService cryptoService) {
        return new TcpMessenger(host, port, cryptoService);
    }

    @SneakyThrows
    private static UdpMessenger createUdpMessenger(final String host, final int port,
            final CryptoService cryptoService) {
        return new UdpMessenger(host, port, cryptoService);
    }

}
