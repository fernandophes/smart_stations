package br.edu.ufersa.cc.seg.common.factories;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;

import br.edu.ufersa.cc.seg.common.concrete_crypto.AESService;
import br.edu.ufersa.cc.seg.common.concrete_crypto.RSAService;
import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.crypto.CryptoServicePair;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class CryptoServiceFactory {

    private static final int KEY_SIZE = 2048;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String RSA_ALGORITHM = "RSA";

    public static CryptoService aes(final byte[] encryptionKey, final byte[] hmacKey) {
        return new AESService(encryptionKey, hmacKey);
    }

    public static CryptoService aes(final String encryptionKey, final String hmacKey) {
        return new AESService(encryptionKey, hmacKey);
    }

    public static CryptoServicePair rsaPair() {
        final var encriptionPair = generateKeys(RSA_ALGORITHM);
        final var hmacPair = generateKeys(RSA_ALGORITHM);

        final var publicSide = new RSAService(encriptionPair.getPublic().getEncoded(),
                hmacPair.getPublic().getEncoded());
        final var privateSide = new RSAService(encriptionPair.getPrivate().getEncoded(),
                hmacPair.getPrivate().getEncoded());

        return new CryptoServicePair(publicSide, privateSide, encriptionPair.getPublic(), hmacPair.getPublic());
    }

    @SneakyThrows
    private static KeyPair generateKeys(final String algorithm) {
        final var generator = KeyPairGenerator.getInstance(algorithm);
        generator.initialize(KEY_SIZE, SECURE_RANDOM);
        return generator.generateKeyPair();
    }

}
