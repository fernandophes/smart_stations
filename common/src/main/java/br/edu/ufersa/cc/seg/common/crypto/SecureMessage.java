package br.edu.ufersa.cc.seg.common.crypto;

import java.io.Serializable;

import lombok.Builder;
import lombok.Data;

/**
 * Representa uma mensagem segura que inclui o conteúdo cifrado,
 * HMAC para integridade/autenticidade, e outros metadados necessários.
 */
@Data
@Builder
public class SecureMessage implements Serializable {

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

}
