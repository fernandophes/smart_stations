package br.edu.ufersa.cc.seg.common.network;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * Interface comum para comunicação segura entre processos
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Messenger implements Closeable {

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public class Subscription<M extends Message> implements Closeable {
        private final Class<M> messageType;
        private final Consumer<M> consumer;
        private Thread thread;

        private AtomicBoolean isRunning = new AtomicBoolean(false);

        private void start() {
            isRunning.set(true);

            thread = new Thread(() -> {
                while (isRunning.get()) {
                    readAndConsume();
                }
            });
        }

        @SneakyThrows
        private void readAndConsume() {
            consumer.accept(receiveAs(messageType));
        }

        @Override
        public void close() throws IOException {
            isRunning.set(false);
            thread.interrupt();
        }
    }

    protected final CryptoService cryptoService;

    public abstract void send(Message message) throws IOException;

    public abstract <M extends Message> M receiveAs(Class<M> messageType) throws IOException;

    public <M extends Message> Subscription<M> subscribe(Class<M> messageType, Consumer<M> consumer) {
        final var subscription = new Subscription<>(messageType, consumer);
        subscription.start();

        return subscription;
    }

}
