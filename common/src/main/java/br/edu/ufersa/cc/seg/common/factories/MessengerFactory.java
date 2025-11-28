package br.edu.ufersa.cc.seg.common.factories;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import br.edu.ufersa.cc.seg.common.concrete_messengers.SecureTcpMessenger;
import br.edu.ufersa.cc.seg.common.concrete_messengers.SecureUdpMessenger;
import br.edu.ufersa.cc.seg.common.concrete_messengers.TcpMessenger;
import br.edu.ufersa.cc.seg.common.concrete_messengers.UdpMessenger;
import br.edu.ufersa.cc.seg.common.crypto.AESService;
import br.edu.ufersa.cc.seg.common.messengers.Messenger;
import br.edu.ufersa.cc.seg.common.messengers.SecureMessenger;
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
    private static final Map<MessengerInfo, SecureTcpMessenger> SECURE_TCP_MESSENGERS = new HashMap<>();
    private static final Map<MessengerInfo, SecureUdpMessenger> SECURE_UDP_MESSENGERS = new HashMap<>();

    public static Messenger tcp(final String host, final int port) {
        final var info = new MessengerInfo(host, port);

        return Optional.ofNullable(TCP_MESSENGERS.get(info))
                .orElseGet(() -> {
                    final var messenger = createTcpMessenger(host, port);
                    TCP_MESSENGERS.put(info, messenger);

                    return messenger;
                });
    }

    public static Messenger udp(final String host, final int port) {
        final var info = new MessengerInfo(host, port);

        return Optional.ofNullable(UDP_MESSENGERS.get(info))
                .orElseGet(() -> {
                    final var messenger = createUdpMessenger(host, port);
                    UDP_MESSENGERS.put(info, messenger);

                    return messenger;
                });
    }

    public static SecureMessenger secureTcp(final String host, final int port, final AESService cryptoService) {
        final var info = new MessengerInfo(host, port);

        return Optional.ofNullable(SECURE_TCP_MESSENGERS.get(info))
                .orElseGet(() -> {
                    final var messenger = createSecureTcpMessenger(host, port, cryptoService);
                    SECURE_TCP_MESSENGERS.put(info, messenger);

                    return messenger;
                });
    }

    public static SecureMessenger secureUdp(final String host, final int port, final AESService cryptoService) {
        final var info = new MessengerInfo(host, port);

        return Optional.ofNullable(SECURE_UDP_MESSENGERS.get(info))
                .orElseGet(() -> {
                    final var messenger = createSecureUdpMessenger(host, port, cryptoService);
                    SECURE_UDP_MESSENGERS.put(info, messenger);

                    return messenger;
                });
    }

    @SneakyThrows
    private static TcpMessenger createTcpMessenger(final String host, final int port) {
        return new TcpMessenger(host, port);
    }

    @SneakyThrows
    private static UdpMessenger createUdpMessenger(final String host, final int port) {
        return new UdpMessenger(host, port);
    }

    @SneakyThrows
    private static SecureTcpMessenger createSecureTcpMessenger(final String host, final int port,
            final AESService cryptoService) {
        return new SecureTcpMessenger(host, port, cryptoService);
    }

    @SneakyThrows
    private static SecureUdpMessenger createSecureUdpMessenger(final String host, final int port,
            final AESService cryptoService) {
        return new SecureUdpMessenger(host, port, cryptoService);
    }

}
