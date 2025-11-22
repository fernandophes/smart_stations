package br.edu.ufersa.cc.seg.common.network;

import java.io.Serializable;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.edu.ufersa.cc.seg.common.utils.Operation;
import lombok.Data;
import lombok.SneakyThrows;

@Data
public abstract class Message implements Serializable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Operation operation;

    @SneakyThrows
    public byte[] toBytes() {
        return MAPPER.writeValueAsBytes(this);
    }

    @SneakyThrows
    public String toJson() {
        return MAPPER.writeValueAsString(this);
    }

}
