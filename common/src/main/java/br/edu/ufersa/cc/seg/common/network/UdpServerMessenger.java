package br.edu.ufersa.cc.seg.common.network;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UdpServerMessenger {

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public class Subscription implements Closeable {
        private final Function<Message, Message> callback;
        private Thread thread;

        private AtomicBoolean isRunning = new AtomicBoolean(false);

        private void start() {
            isRunning.set(true);

            thread = new Thread(() -> {
                while (isRunning.get()) {
                    log.info("Aguardando clientes...");

                    final var client = accept();

                    log.info("Novo cliente");
                    final var clientSubscription = client.getMessenger().subscribe(callback);
                    clientSubscription.handleRequest(client.getFirstMessage());
                }
            });

            thread.start();
        }

        @Override
        public void close() throws IOException {
            isRunning.set(false);
            thread.interrupt();
        }
    }

    @Value
    public static class ClientRegistration {
        UdpClientMessenger messenger;
        Message firstMessage;
    }

    private final DatagramSocket socket;
    private final CryptoService cryptoService;

    public UdpServerMessenger(final CryptoService cryptoService) throws IOException {
        this(new DatagramSocket(), cryptoService);
    }

    public UdpServerMessenger(final int port, final CryptoService cryptoService) throws IOException {
        this(new DatagramSocket(port), cryptoService);
    }

    private UdpServerMessenger(final DatagramSocket socket, final CryptoService cryptoService) {
        this.socket = socket;
        this.cryptoService = cryptoService;
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    @SneakyThrows
    public ClientRegistration accept() {
        final var bytes = new byte[1024];
        final var packet = new DatagramPacket(bytes, bytes.length);
        socket.receive(packet);

        final var client = new UdpClientMessenger(packet.getAddress().getHostName(), packet.getPort(), cryptoService);

        return new ClientRegistration(client, client.receive(packet));
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
