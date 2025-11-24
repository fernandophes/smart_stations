package br.edu.ufersa.cc.seg.common.factories;

import br.edu.ufersa.cc.seg.common.network.Message;
import br.edu.ufersa.cc.seg.common.utils.MessageType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class MessageFactory {

    public static Message ok() {
        return new Message(MessageType.OK);
    }

    public static Message ok(final String key, final Object value) {
        return new Message(MessageType.OK).withValue(key, value);
    }

    public static Message error() {
        return new Message(MessageType.ERROR);
    }

    public static Message error(final String message) {
        return new Message(MessageType.ERROR).withValue("message", message);
    }

}
