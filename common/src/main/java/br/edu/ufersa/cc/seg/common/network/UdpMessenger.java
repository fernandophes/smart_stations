package br.edu.ufersa.cc.seg.common.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.crypto.SecureMessage;
import lombok.SneakyThrows;

public class UdpMessenger extends Messenger {

    private final DatagramSocket socket;

    public UdpMessenger(final DatagramSocket socket, final CryptoService cryptoService) throws IOException {
        super(cryptoService);

        this.socket = socket;
    }

    public UdpMessenger(final String host, final int port, final CryptoService cryptoService) throws IOException {
        this(new DatagramSocket(port, InetAddress.getByName(host)), cryptoService);
    }

    public UdpMessenger(final int port, final CryptoService cryptoService) throws IOException {
        this(new DatagramSocket(port), cryptoService);
    }

    @Override
    @SneakyThrows
    public void send(final Message message) {
        final var secureMsg = cryptoService.encrypt(message.toBytes()).toBytes();
        final var packet = new DatagramPacket(secureMsg, secureMsg.length);

        socket.send(packet);
    }

    @Override
    @SneakyThrows
    public Message receive() {
        final var bytes = new byte[256];
        final var packet = new DatagramPacket(bytes, bytes.length);
        socket.receive(packet);

        final var secureMsg = SecureMessage.fromBytes(packet.getData());
        final var messageAsBytes = cryptoService.decrypt(secureMsg);

        return Message.fromBytes(messageAsBytes);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

}
