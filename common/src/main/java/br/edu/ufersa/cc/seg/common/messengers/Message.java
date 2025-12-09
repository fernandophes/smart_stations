package br.edu.ufersa.cc.seg.common.messengers;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.edu.ufersa.cc.seg.common.utils.MessageType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@Data
@NoArgsConstructor
public class Message implements Serializable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MessageType type;
    private final Map<String, Object> values = new HashMap<>();

    public Message(final MessageType type) {
        this.type = type;
    }

    @SneakyThrows
    public static Message fromBytes(final byte[] bytes) {
        return MAPPER.readValue(bytes, Message.class);
    }

    @SneakyThrows
    public static Message fromJson(final String json) {
        return MAPPER.readValue(json, Message.class);
    }

    @SneakyThrows
    public byte[] toBytes() {
        return MAPPER.writeValueAsBytes(this);
    }

    @SneakyThrows
    public String toJson() {
        return MAPPER.writeValueAsString(this);
    }

    public Message withValue(final String key, final Object value) {
        values.put(key, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T getValue(final String key) {
        return (T) values.get(key);
    }

}
