package br.edu.ufersa.cc.seg.common.network;

import java.io.Closeable;
import java.util.function.Function;

public interface ServerMessenger extends Closeable {

    int getPort();

    Closeable subscribe(final Function<Message, Message> callback);

}
