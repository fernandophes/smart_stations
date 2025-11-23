package br.edu.ufersa.cc.seg.common.crypto;

import java.io.Serializable;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;

/**
 * Representa uma mensagem segura que inclui o conteúdo cifrado,
 * HMAC para integridade/autenticidade, e outros metadados necessários.
 */
@Data
@Builder
public class SecureMessage implements Serializable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Conteúdo cifrado com algoritmo simétrico
     */
    private byte[] encryptedContent;

    /**
     * HMAC para integridade e autenticidade
     */
    private byte[] hmac;

    /**
     * Vetor de inicialização para cifra
     */
    private byte[] iv;

    /**
     * Timestamp para evitar replay attacks
     */
    private long timestamp;

    @SneakyThrows
    public static SecureMessage fromBytes(final byte[] bytes) {
        return MAPPER.readValue(bytes, SecureMessage.class);
    }

    @SneakyThrows
    public static SecureMessage fromJson(final String json) {
        return MAPPER.readValue(json, SecureMessage.class);
    }

    @SneakyThrows
    public byte[] toBytes() {
        return MAPPER.writeValueAsBytes(this);
    }

    @SneakyThrows
    public String toJson() {
        return MAPPER.writeValueAsString(this);
    }

}
