package br.edu.ufersa.cc.seg.common.concrete_messengers;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.Messenger;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UdpMessenger extends Messenger {

    private final DatagramSocket socket;
    private final InetAddress destinationHost;
    private final int destinationPort;

    public UdpMessenger(final String destinationHost, final int destinationPort) throws IOException {
        this.socket = new DatagramSocket();
        this.destinationHost = InetAddress.getByName(destinationHost);
        this.destinationPort = destinationPort;
    }

    @Override
    public int getPort() {
        return socket.getLocalPort();
    }

    @Override
    @SneakyThrows
    public void send(final Message message) {
        final var messageAsBytes = message.toBytes();
        final var packet = new DatagramPacket(messageAsBytes, messageAsBytes.length, destinationHost, destinationPort);

        log.info("Enviando mensagem do tipo {} para {}:{}", message.getType(), destinationHost, destinationPort);
        socket.send(packet);
    }

    @Override
    @SneakyThrows
    public Message receive() {
        final var bytes = new byte[2048];
        final var packet = new DatagramPacket(bytes, bytes.length);
        socket.receive(packet);

        return receive(packet);
    }

    public Message receive(final DatagramPacket packet) {
        final var message = Message.fromBytes(packet.getData());

        log.info("Recebida mensagem do tipo {} de {}:{}", message.getType(), packet.getAddress().getHostName(),
                packet.getPort());
        return message;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

}
