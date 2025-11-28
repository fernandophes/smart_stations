package br.edu.ufersa.cc.seg.common.factories;

import br.edu.ufersa.cc.seg.common.concrete_crypto.AESService;
import br.edu.ufersa.cc.seg.common.concrete_crypto.RSAService;
import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class CryptoServiceFactory {

    public static CryptoService aes(final byte[] encryptionKey, final byte[] hmacKey) {
        return new AESService(encryptionKey, hmacKey);
    }

    public static CryptoService aes(final String encryptionKey, final String hmacKey) {
        return new AESService(encryptionKey, hmacKey);
    }

    public static CryptoService rsa(final byte[] hmacKey) {
        return new RSAService(hmacKey);
    }

    public static CryptoService rsa(final String hmacKey) {
        return new RSAService(hmacKey);
    }

}
