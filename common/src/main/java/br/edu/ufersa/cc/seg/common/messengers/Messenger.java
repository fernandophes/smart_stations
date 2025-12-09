package br.edu.ufersa.cc.seg.common.messengers;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class Messenger implements Closeable {

    private final Set<Subscription> subscriptions = new HashSet<>();

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public class Subscription implements Closeable {
        private final Function<Message, Message> callback;
        private Thread thread;

        private AtomicBoolean isRunning = new AtomicBoolean(false);

        public void handleRequest(final Message request) {
            final var response = callback.apply(request);
            send(response);
        }

        private void start() {
            isRunning.set(true);
            subscriptions.add(this);

            thread = new Thread(() -> {
                while (isRunning.get()) {
                    try {
                        log.info("Aguardando requisições...");
                        handleRequest(receive());
                    } catch (final IOException e) {
                        log.info("Parando leitura...");
                    }
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

    public abstract int getPort();

    public abstract void send(Message message);

    public abstract Message receive() throws IOException;

    public Subscription subscribe(final Function<Message, Message> callback) {
        final var subscription = new Subscription(callback);
        subscription.start();

        return subscription;
    }

    protected void closeSubscriptions() {
        subscriptions.forEach(subscription -> {
            try {
                if (subscription.isRunning.get()) {
                    subscription.close();
                }
            } catch (final IOException e) {
                // Ignorar
            }
        });

        subscriptions.clear();
    }

}
