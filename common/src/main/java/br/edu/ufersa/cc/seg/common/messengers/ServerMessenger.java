package br.edu.ufersa.cc.seg.common.messengers;

import java.io.Closeable;
import java.util.Set;
import java.util.function.Function;

public interface ServerMessenger extends Closeable {

    int getPort();

    Closeable subscribe(final Function<Message, Message> callback);

    Set<Messenger> getClients();

}
