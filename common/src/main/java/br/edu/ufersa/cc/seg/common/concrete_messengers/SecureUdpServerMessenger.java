package br.edu.ufersa.cc.seg.common.concrete_messengers;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.Messenger;
import br.edu.ufersa.cc.seg.common.messengers.ServerMessenger;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SecureUdpServerMessenger implements ServerMessenger {

    private final Set<Messenger> clients = new HashSet<>();

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public class Subscription implements Closeable {
        private final Function<Message, Message> callback;
        private Thread thread;

        private AtomicBoolean isRunning = new AtomicBoolean(false);

        private void start() {
            isRunning.set(true);

            thread = new Thread(() -> {
                while (isRunning.get()) {
                    try {
                        final var client = accept();
                        clients.add(client.getMessenger());

                        final var clientSubscription = client.getMessenger().subscribe(callback);
                        clientSubscription.handleRequest(client.getFirstMessage());
                    } catch (final IOException e) {
                        log.info("Parando leitura...");
                        close();
                    }
                }
            });

            thread.start();
        }

        private ClientRegistration accept() throws IOException {
            final var bytes = new byte[2048];
            final var packet = new DatagramPacket(bytes, bytes.length);
            socket.receive(packet);

            final var client = new SecureUdpMessenger(packet.getAddress().getHostName(), packet.getPort(),
                    cryptoService);

            return new ClientRegistration(client, client.receive(packet));
        }

        @Override
        public void close() {
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
    private final CryptoService cryptoService;

    public SecureUdpServerMessenger(final CryptoService cryptoService) throws IOException {
        this(new DatagramSocket(), cryptoService);
    }

    public SecureUdpServerMessenger(final int port, final CryptoService cryptoService) throws IOException {
        this(new DatagramSocket(port), cryptoService);
    }

    public SecureUdpServerMessenger(final DatagramSocket socket, final CryptoService cryptoService) {
        this.socket = socket;
        this.cryptoService = cryptoService;
    }

    @Override
    public int getPort() {
        return socket.getLocalPort();
    }

    @Override
    public Subscription subscribe(final Function<Message, Message> callback) {
        final var subscription = new Subscription(callback);
        subscription.start();
        return subscription;
    }

    @Override
    public void close() {
        // Fechar clientes
        clients.forEach(client -> {
            try {
                client.close();
            } catch (final IOException ignore) {
                // Ignorar
            }
        });
        clients.clear();

        // Fechar servidor
        socket.close();
    }

}
