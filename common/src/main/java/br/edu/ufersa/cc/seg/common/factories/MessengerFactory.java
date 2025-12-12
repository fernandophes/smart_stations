package br.edu.ufersa.cc.seg.common.factories;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import br.edu.ufersa.cc.seg.common.concrete_messengers.SecureTcpMessenger;
import br.edu.ufersa.cc.seg.common.concrete_messengers.SecureUdpMessenger;
import br.edu.ufersa.cc.seg.common.concrete_messengers.TcpMessenger;
import br.edu.ufersa.cc.seg.common.concrete_messengers.UdpMessenger;
import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.messengers.Messenger;
import br.edu.ufersa.cc.seg.common.messengers.SecureMessenger;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class MessengerFactory {

    @FunctionalInterface
    private static interface TriFunction<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    @Data
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
        return findOrCreate(TCP_MESSENGERS, info, MessengerFactory::createTcpMessenger);
    }

    public static Messenger udp(final String host, final int port) {
        final var info = new MessengerInfo(host, port);
        return findOrCreate(UDP_MESSENGERS, info, MessengerFactory::createUdpMessenger);
    }

    public static SecureMessenger secureTcp(final String host, final int port, final CryptoService cryptoService) {
        final var info = new MessengerInfo(host, port);
        return findOrCreate(SECURE_TCP_MESSENGERS, info, cryptoService, MessengerFactory::createSecureTcpMessenger);
    }

    public static SecureMessenger secureUdp(final String host, final int port, final CryptoService cryptoService) {
        final var info = new MessengerInfo(host, port);
        return findOrCreate(SECURE_UDP_MESSENGERS, info, cryptoService, MessengerFactory::createSecureUdpMessenger);
    }

    private static <M extends Messenger> M findOrCreate(final Map<MessengerInfo, M> map, final MessengerInfo info,
            final BiFunction<String, Integer, M> creator) {
        return Optional.ofNullable(map.get(info))
                .flatMap(messenger -> {
                    if (messenger.isClosed()) {
                        return Optional.empty();
                    } else {
                        map.remove(info);
                        return Optional.of(messenger);
                    }
                })
                .orElseGet(() -> {
                    final var messenger = creator.apply(info.getHost(), info.getPort());
                    map.put(info, messenger);

                    return messenger;
                });
    }

    private static <S extends SecureMessenger> S findOrCreate(final Map<MessengerInfo, S> map, final MessengerInfo info,
            final CryptoService cryptoService, final TriFunction<String, Integer, CryptoService, S> creator) {
        return Optional.ofNullable(map.get(info))
                .flatMap(messenger -> {
                    if (messenger.isClosed()) {
                        return Optional.empty();
                    } else {
                        map.remove(info);
                        return Optional.of(messenger);
                    }
                })
                .orElseGet(() -> {
                    final S messenger = creator.apply(info.getHost(), info.getPort(), cryptoService);
                    map.put(info, messenger);

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
            final CryptoService cryptoService) {
        return new SecureTcpMessenger(host, port, cryptoService);
    }

    @SneakyThrows
    private static SecureUdpMessenger createSecureUdpMessenger(final String host, final int port,
            final CryptoService cryptoService) {
        return new SecureUdpMessenger(host, port, cryptoService);
    }

}
