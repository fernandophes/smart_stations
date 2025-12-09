package br.edu.ufersa.cc.seg.common.concrete_messengers;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.Messenger;
import br.edu.ufersa.cc.seg.common.messengers.ServerMessenger;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UdpServerMessenger implements ServerMessenger {

    @Getter
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
                    final var client = accept();
                    clients.add(client.getMessenger());

                    final var clientSubscription = client.getMessenger().subscribe(callback);
                    clientSubscription.handleRequest(client.getFirstMessage());
                }
            });

            thread.start();
        }

        @SneakyThrows
        private ClientRegistration accept() {
            final var bytes = new byte[2048];
            final var packet = new DatagramPacket(bytes, bytes.length);
            socket.receive(packet);

            final var client = new UdpMessenger(packet.getAddress().getHostName(), packet.getPort());

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
        UdpMessenger messenger;
        Message firstMessage;
    }

    private final DatagramSocket socket;

    public UdpServerMessenger() throws IOException {
        this(new DatagramSocket());
    }

    public UdpServerMessenger(final int port) throws IOException {
        this(new DatagramSocket(port));
    }

    private UdpServerMessenger(final DatagramSocket socket) {
        this.socket = socket;
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
        // Fechar clientes
        clients.forEach(client -> {
            try {
                client.close();
                clients.remove(client);
            } catch (final IOException ignore) {
                // Ignorar
            }
        });

        // Fechar servidor
        socket.close();
    }

}
