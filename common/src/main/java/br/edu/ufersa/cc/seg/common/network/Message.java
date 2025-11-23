package br.edu.ufersa.cc.seg.common.network;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.edu.ufersa.cc.seg.common.utils.MessageType;
import lombok.Data;
import lombok.SneakyThrows;

@Data
public class Message implements Serializable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MessageType type;
    private final Map<String, Object> values = new HashMap<>();

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

}
