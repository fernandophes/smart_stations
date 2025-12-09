package br.edu.ufersa.cc.seg.common.concrete_messengers;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TcpServerMessenger implements ServerMessenger {

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
                    log.info("Aguardando clientes...");

                    final var client = accept();
                    clients.add(client);
                    client.subscribe(callback);
                }
            });

            thread.start();
        }

        @Override
        public void close() throws IOException {
            isRunning.set(false);
            thread.interrupt();
        }

        @SneakyThrows
        private TcpMessenger accept() {
            return new TcpMessenger(serverSocket);
        }
    }

    private final ServerSocket serverSocket;

    public TcpServerMessenger() throws IOException {
        this(new ServerSocket(0));
    }

    public TcpServerMessenger(final int port) throws IOException {
        this(new ServerSocket(port));
    }

    private TcpServerMessenger(final ServerSocket socket) {
        this.serverSocket = socket;
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public Subscription subscribe(final Function<Message, Message> callback) {
        final var subscription = new Subscription(callback);
        subscription.start();
        return subscription;
    }

    @SneakyThrows
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
        serverSocket.close();
    }

}
