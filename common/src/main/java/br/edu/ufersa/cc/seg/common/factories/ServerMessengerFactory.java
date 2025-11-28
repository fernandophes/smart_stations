package br.edu.ufersa.cc.seg.common.factories;

import br.edu.ufersa.cc.seg.common.concrete_messengers.SecureTcpServerMessenger;
import br.edu.ufersa.cc.seg.common.concrete_messengers.SecureUdpServerMessenger;
import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.messengers.ServerMessenger;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class ServerMessengerFactory {

    @SneakyThrows
    public static ServerMessenger tcp(final CryptoService cryptoService) {
        return new SecureTcpServerMessenger(cryptoService);
    }

    @SneakyThrows
    public static ServerMessenger tcp(final int port, final CryptoService cryptoService) {
        return new SecureTcpServerMessenger(port, cryptoService);
    }

    @SneakyThrows
    public static ServerMessenger udp(final CryptoService cryptoService) {
        return new SecureUdpServerMessenger(cryptoService);
    }

    @SneakyThrows
    public static ServerMessenger udp(final int port, final CryptoService cryptoService) {
        return new SecureUdpServerMessenger(port, cryptoService);
    }

}
