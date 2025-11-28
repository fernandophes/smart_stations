package br.edu.ufersa.cc.seg.common.concrete_messengers;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import br.edu.ufersa.cc.seg.common.crypto.AESService;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.ServerMessenger;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SecureTcpServerMessenger implements ServerMessenger {

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
        private SecureTcpMessenger accept() {
            return new SecureTcpMessenger(serverSocket, cryptoService);
        }
    }

    private final ServerSocket serverSocket;
    private final AESService cryptoService;

    public SecureTcpServerMessenger(final AESService cryptoService) throws IOException {
        this(new ServerSocket(0), cryptoService);
    }

    public SecureTcpServerMessenger(final int port, final AESService cryptoService) throws IOException {
        this(new ServerSocket(port), cryptoService);
    }

    private SecureTcpServerMessenger(final ServerSocket socket, final AESService cryptoService) {
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
        serverSocket.close();
    }

}
