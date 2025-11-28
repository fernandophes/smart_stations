package br.edu.ufersa.cc.seg.common.concrete_messengers;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.ServerMessenger;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TcpServerMessenger implements ServerMessenger {

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
            return new SecureTcpMessenger(socket, cryptoService);
        }
    }

    @Value
    public static class ClientRegistration {
        SecureTcpMessenger messenger;
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
