package br.edu.ufersa.cc.seg.common.crypto;

/**
 * Exceção para erros relacionados à criptografia
 */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }

}