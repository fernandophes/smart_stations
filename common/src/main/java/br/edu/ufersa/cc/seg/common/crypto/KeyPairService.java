package br.edu.ufersa.cc.seg.common.crypto;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

import javax.crypto.Cipher;

import lombok.SneakyThrows;

public class KeyPairService {

    public static final int KEY_SIZE = 2048;
    public static final String TRANSFORMATION = "RSA";

    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private final SecureRandom secureRandom;

    public KeyPairService() {
        this.secureRandom = new SecureRandom();

        final var pair = generateKeys();
        this.privateKey = pair.getPrivate();
        this.publicKey = pair.getPublic();
    }

    @SneakyThrows
    public byte[] encode(final byte[] message) {
        final var cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, privateKey);
        return cipher.doFinal(message);
    }

    @SneakyThrows
    public byte[] decode(final byte[] message) {
        final var cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, publicKey);
        return cipher.doFinal(message);
    }

    @SneakyThrows
    private KeyPair generateKeys() {
        final var generator = KeyPairGenerator.getInstance(TRANSFORMATION);
        generator.initialize(KEY_SIZE, secureRandom);
        return generator.generateKeyPair();
    }

}
