package br.edu.ufersa.cc.seg.common.concrete_messengers;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import br.edu.ufersa.cc.seg.common.crypto.AESService;
import br.edu.ufersa.cc.seg.common.crypto.SecureMessage;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.SecureMessenger;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SecureUdpMessenger extends SecureMessenger {

    private final DatagramSocket socket;
    private final InetAddress destinationHost;
    private final int destinationPort;

    public SecureUdpMessenger(final String destinationHost, final int destinationPort,
            final AESService cryptoService) throws IOException {
        super(cryptoService);
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
        final var secureMessage = cryptoService.encrypt(message.toBytes()).toBytes();
        final var packet = new DatagramPacket(secureMessage, secureMessage.length, destinationHost, destinationPort);

        log.info("Enviando mensagem do tipo {} para {}:{}", message.getType(), destinationHost, destinationPort);
        socket.send(packet);
    }

    @Override
    @SneakyThrows
    public Message receive() {
        final var bytes = new byte[1024];
        final var packet = new DatagramPacket(bytes, bytes.length);
        socket.receive(packet);

        return receive(packet);
    }

    public Message receive(final DatagramPacket packet) {
        final var secureMessage = SecureMessage.fromBytes(packet.getData());
        final var messageAsBytes = cryptoService.decrypt(secureMessage);
        final var message = Message.fromBytes(messageAsBytes);

        log.info("Recebida mensagem do tipo {} de {}:{}", message.getType(), packet.getAddress().getHostName(),
                packet.getPort());
        return message;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

}
