package br.edu.ufersa.cc.seg.common.factories;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import br.edu.ufersa.cc.seg.common.concrete_crypto.AESService;
import br.edu.ufersa.cc.seg.common.concrete_crypto.AsymmetricMode;
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

    public static CryptoService aes(final SecretKey encryptionKey, final SecretKey hmacKey) {
        return new AESService(encryptionKey, hmacKey);
    }

    public static CryptoService aes(final byte[] encryptionKey, final byte[] hmacKey) {
        return new AESService(encryptionKey, hmacKey);
    }

    public static CryptoService aes(final String encryptionKey, final String hmacKey) {
        return new AESService(encryptionKey, hmacKey);
    }

    public static CryptoService publicRsa(final byte[] encryptionKey) {
        return new RSAService(AsymmetricMode.PUBLIC, encryptionKey);
    }

    public static CryptoService publicRsa(final String encryptionKey) {
        return new RSAService(AsymmetricMode.PUBLIC, encryptionKey);
    }

    public static CryptoService publicRsa(final Key encryptionKey) {
        return new RSAService(AsymmetricMode.PUBLIC, encryptionKey.getEncoded());
    }

    public static CryptoServicePair rsaPair() {
        final var keyPair = generateKeys(RSA_ALGORITHM);

        final var publicSide = new RSAService(AsymmetricMode.PUBLIC, keyPair.getPublic().getEncoded());
        final var privateSide = new RSAService(AsymmetricMode.PRIVATE, keyPair.getPrivate().getEncoded());

        return new CryptoServicePair(publicSide, privateSide, keyPair.getPublic());
    }

    @SneakyThrows
    public static SecretKey generateAESKey() {
        final var keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128);
        return keyGenerator.generateKey();
    }

    @SneakyThrows
    private static KeyPair generateKeys(final String algorithm) {
        final var generator = KeyPairGenerator.getInstance(algorithm);
        generator.initialize(KEY_SIZE, SECURE_RANDOM);
        return generator.generateKeyPair();
    }

}
