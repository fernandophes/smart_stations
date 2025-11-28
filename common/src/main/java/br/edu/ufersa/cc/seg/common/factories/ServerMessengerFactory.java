package br.edu.ufersa.cc.seg.common.factories;

import br.edu.ufersa.cc.seg.common.concrete_messengers.SecureTcpServerMessenger;
import br.edu.ufersa.cc.seg.common.concrete_messengers.SecureUdpServerMessenger;
import br.edu.ufersa.cc.seg.common.concrete_messengers.TcpServerMessenger;
import br.edu.ufersa.cc.seg.common.concrete_messengers.UdpServerMessenger;
import br.edu.ufersa.cc.seg.common.crypto.AESService;
import br.edu.ufersa.cc.seg.common.messengers.ServerMessenger;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class ServerMessengerFactory {

    @SneakyThrows
    public static ServerMessenger tcp() {
        return new TcpServerMessenger();
    }

    @SneakyThrows
    public static ServerMessenger tcp(final int port) {
        return new TcpServerMessenger(port);
    }

    @SneakyThrows
    public static ServerMessenger udp() {
        return new UdpServerMessenger();
    }

    @SneakyThrows
    public static ServerMessenger udp(final int port) {
        return new UdpServerMessenger(port);
    }

    @SneakyThrows
    public static ServerMessenger secureTcp(final AESService cryptoService) {
        return new SecureTcpServerMessenger(cryptoService);
    }

    @SneakyThrows
    public static ServerMessenger secureTcp(final int port, final AESService cryptoService) {
        return new SecureTcpServerMessenger(port, cryptoService);
    }

    @SneakyThrows
    public static ServerMessenger secureUdp(final AESService cryptoService) {
        return new SecureUdpServerMessenger(cryptoService);
    }

    @SneakyThrows
    public static ServerMessenger secureUdp(final int port, final AESService cryptoService) {
        return new SecureUdpServerMessenger(port, cryptoService);
    }

}
