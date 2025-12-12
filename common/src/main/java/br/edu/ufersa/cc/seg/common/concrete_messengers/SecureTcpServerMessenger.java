package br.edu.ufersa.cc.seg.common.concrete_messengers;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SecureTcpServerMessenger implements ServerMessenger {

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
                        log.info("Aguardando clientes...");

                        final var client = accept();
                        clients.add(client);
                        client.subscribe(callback);
                    } catch (IOException e) {
                        log.info("Parando leitura...");
                        close();
                    }
                }
            });

            thread.start();
        }

        @Override
        public void close() {
            isRunning.set(false);
            thread.interrupt();
        }

        private SecureTcpMessenger accept() throws IOException {
            return new SecureTcpMessenger(serverSocket, cryptoService);
        }
    }

    private final ServerSocket serverSocket;
    private final CryptoService cryptoService;

    public SecureTcpServerMessenger(final CryptoService cryptoService) throws IOException {
        this(new ServerSocket(0), cryptoService);
    }

    public SecureTcpServerMessenger(final int port, final CryptoService cryptoService) throws IOException {
        this(new ServerSocket(port), cryptoService);
    }

    public SecureTcpServerMessenger(final ServerSocket socket, final CryptoService cryptoService) {
        this.serverSocket = socket;
        this.cryptoService = cryptoService;
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
            } catch (final IOException ignore) {
                // Ignorar
            }
        });
        clients.clear();

        // Fechar servidor
        serverSocket.close();
    }

}
