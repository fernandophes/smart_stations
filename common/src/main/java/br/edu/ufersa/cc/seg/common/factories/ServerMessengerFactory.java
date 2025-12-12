package br.edu.ufersa.cc.seg.common.factories;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;

import br.edu.ufersa.cc.seg.common.concrete_messengers.SecureTcpServerMessenger;
import br.edu.ufersa.cc.seg.common.concrete_messengers.SecureUdpServerMessenger;
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
    public static ServerMessenger tcp() {
        return new TcpServerMessenger();
    }

    @SneakyThrows
    public static ServerMessenger tcp(final int port) {
        return new TcpServerMessenger(port);
    }

    @SneakyThrows
    public static ServerMessenger tcp(final String host, final int port) {
        return new TcpServerMessenger(new ServerSocket(port, 50, InetAddress.getByName(host)));
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
    public static ServerMessenger udp(final String host, final int port) {
        return new UdpServerMessenger(new DatagramSocket(port, InetAddress.getByName(host)));
    }

    @SneakyThrows
    public static ServerMessenger secureTcp(final CryptoService cryptoService) {
        return new SecureTcpServerMessenger(cryptoService);
    }

    @SneakyThrows
    public static ServerMessenger secureTcp(final int port, final CryptoService cryptoService) {
        return new SecureTcpServerMessenger(port, cryptoService);
    }

    @SneakyThrows
    public static ServerMessenger secureTcp(final String host, final int port, final CryptoService cryptoService) {
        return new SecureTcpServerMessenger(new ServerSocket(port, 50, InetAddress.getByName(host)), cryptoService);
    }

    @SneakyThrows
    public static ServerMessenger secureUdp(final CryptoService cryptoService) {
        return new SecureUdpServerMessenger(cryptoService);
    }

    @SneakyThrows
    public static ServerMessenger secureUdp(final int port, final CryptoService cryptoService) {
        return new SecureUdpServerMessenger(port, cryptoService);
    }

    @SneakyThrows
    public static ServerMessenger secureUdp(final String host, final int port, final CryptoService cryptoService) {
        return new SecureUdpServerMessenger(new DatagramSocket(port, InetAddress.getByName(host)), cryptoService);
    }

}
