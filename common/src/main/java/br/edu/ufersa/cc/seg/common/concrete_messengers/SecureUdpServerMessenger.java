package br.edu.ufersa.cc.seg.common.concrete_messengers;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import br.edu.ufersa.cc.seg.common.crypto.AESService;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.ServerMessenger;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SecureUdpServerMessenger implements ServerMessenger {

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public class Subscription implements Closeable {
        private final Function<Message, Message> callback;
        private Thread thread;

        private AtomicBoolean isRunning = new AtomicBoolean(false);

        private void start() {
            isRunning.set(true);

            thread = new Thread(() -> {
                while (isRunning.get()) {
                    final var client = accept();

                    final var clientSubscription = client.getMessenger().subscribe(callback);
                    clientSubscription.handleRequest(client.getFirstMessage());
                }
            });

            thread.start();
        }

        @SneakyThrows
        private ClientRegistration accept() {
            final var bytes = new byte[1024];
            final var packet = new DatagramPacket(bytes, bytes.length);
            socket.receive(packet);

            final var client = new SecureUdpMessenger(packet.getAddress().getHostName(), packet.getPort(), cryptoService);

            return new ClientRegistration(client, client.receive(packet));
        }

        @Override
        public void close() throws IOException {
            isRunning.set(false);
            thread.interrupt();
        }
    }

    @Value
    public static class ClientRegistration {
        SecureUdpMessenger messenger;
        Message firstMessage;
    }

    private final DatagramSocket socket;
    private final AESService cryptoService;

    public SecureUdpServerMessenger(final AESService cryptoService) throws IOException {
        this(new DatagramSocket(), cryptoService);
    }

    public SecureUdpServerMessenger(final int port, final AESService cryptoService) throws IOException {
        this(new DatagramSocket(port), cryptoService);
    }

    private SecureUdpServerMessenger(final DatagramSocket socket, final AESService cryptoService) {
        this.socket = socket;
        this.cryptoService = cryptoService;
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    public Subscription subscribe(final Function<Message, Message> callback) {
        final var subscription = new Subscription(callback);
        subscription.start();
        return subscription;
    }

    public void close() {
        socket.close();
    }

}
