package br.edu.ufersa.cc.seg.common.factories;

import br.edu.ufersa.cc.seg.common.concrete_messengers.TcpServerMessenger;
import br.edu.ufersa.cc.seg.common.concrete_messengers.UdpServerMessenger;
import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.messengers.ServerMessenger;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class ServerMessengerFactory {

    @SneakyThrows
    public static ServerMessenger tcp(final CryptoService cryptoService) {
        return new TcpServerMessenger(cryptoService);
    }

    @SneakyThrows
    public static ServerMessenger tcp(final int port, final CryptoService cryptoService) {
        return new TcpServerMessenger(port, cryptoService);
    }

    @SneakyThrows
    public static ServerMessenger udp(final CryptoService cryptoService) {
        return new UdpServerMessenger(cryptoService);
    }

    @SneakyThrows
    public static ServerMessenger udp(final int port, final CryptoService cryptoService) {
        return new UdpServerMessenger(port, cryptoService);
    }

}
