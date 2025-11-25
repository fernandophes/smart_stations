package br.edu.ufersa.cc.seg.common.network;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TcpServerMessenger {

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
    }

    @Value
    public static class ClientRegistration {
        TcpMessenger messenger;
        Message firstMessage;
    }

    private final ServerSocket socket;
    private final CryptoService cryptoService;

    public TcpServerMessenger(final CryptoService cryptoService) throws IOException {
        this(new ServerSocket(0), cryptoService);
    }

    public TcpServerMessenger(final int port, final CryptoService cryptoService) throws IOException {
        this(new ServerSocket(port), cryptoService);
    }

    private TcpServerMessenger(final ServerSocket socket, final CryptoService cryptoService) {
        this.socket = socket;
        this.cryptoService = cryptoService;
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    @SneakyThrows
    public TcpMessenger accept() {
        return new TcpMessenger(socket, cryptoService);
    }

    public Subscription subscribe(final Function<Message, Message> callback) {
        final var subscription = new Subscription(callback);
        subscription.start();
        return subscription;
    }

    @SneakyThrows
    public void close() {
        socket.close();
    }

}
