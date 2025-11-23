package br.edu.ufersa.cc.seg.common.network;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Interface comum para comunicação segura entre processos
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Messenger implements Closeable {

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public class Subscription implements Closeable {
        private final Function<Message, Message> callback;
        private Thread thread;

        private AtomicBoolean isRunning = new AtomicBoolean(false);

        private void start() {
            isRunning.set(true);

            thread = new Thread(() -> {
                while (isRunning.get()) {
                    final var request = receive();
                    final var response = callback.apply(request);
                    send(response);
                }
            });
        }

        @Override
        public void close() throws IOException {
            isRunning.set(false);
            thread.interrupt();
        }
    }

    protected final CryptoService cryptoService;

    public abstract void send(Message message);

    public abstract Message receive();

    public Subscription subscribe(Function<Message, Message> callback) {
        final var subscription = new Subscription(callback);
        subscription.start();

        return subscription;
    }

}
