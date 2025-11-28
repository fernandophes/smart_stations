package br.edu.ufersa.cc.seg.common.concrete_crypto;

import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.edu.ufersa.cc.seg.common.crypto.CryptoException;
import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.crypto.SecureMessage;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RSAService implements CryptoService {

    private static final String CIPHER_ALGORITHM = "RSA";

    private final Key encryptionKey;

    public RSAService(final AsymmetricMode mode, final byte[] encryptionKey) {
        if (AsymmetricMode.PRIVATE.equals(mode)) {
            this.encryptionKey = toPrivateKey(encryptionKey, CIPHER_ALGORITHM);
        } else {
            this.encryptionKey = toPublicKey(encryptionKey, CIPHER_ALGORITHM);
        }
    }

    public RSAService(final AsymmetricMode mode, final String encryptionKey) {
        this(mode, Base64.getDecoder().decode(encryptionKey));
    }

    @Override
    @SneakyThrows
    public SecureMessage encrypt(final byte[] message) {
        log.debug("Criptografando mensagem...\n{}", new String(message));

        // Cifrar a mensagem
        final var cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey);
        final var encrypted = cipher.doFinal(message);

        // Gera HMAC (encrypted + iv + timestamp para evitar replay)
        final var timestamp = System.currentTimeMillis();

        // Retorna mensagem segura
        final var secureMessage = SecureMessage.builder()
                .encryptedContent(encrypted)
                .timestamp(timestamp)
                .build();

        final var writer = new ObjectMapper().writerWithDefaultPrettyPrinter();
        log.debug("Mensagem criptografada:\n{}", writer.writeValueAsString(secureMessage));

        return secureMessage;
    }

    @Override
    @SneakyThrows
    public byte[] decrypt(final SecureMessage secureMessage) {
        final var writer = new ObjectMapper().writerWithDefaultPrettyPrinter();
        log.debug("Descriptografando mensagem...\n{}", writer.writeValueAsString(secureMessage));

        final var cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey);
        final var original = cipher.doFinal(secureMessage.getEncryptedContent());
        log.debug("Mensagem descriptografada:\n{}", new String(original));

        return original;
    }

    private static PublicKey toPublicKey(final byte[] keyBytes, final String algorithm) {
        try {
            final var spec = new X509EncodedKeySpec(keyBytes);
            final var kf = KeyFactory.getInstance(algorithm);
            return kf.generatePublic(spec);
        } catch (final Exception e) {
            throw new CryptoException("Erro ao construir PublicKey", e);
        }
    }

    private static PrivateKey toPrivateKey(final byte[] keyBytes, final String algorithm) {
        try {
            final var spec = new PKCS8EncodedKeySpec(keyBytes);
            final var kf = KeyFactory.getInstance(algorithm);
            return kf.generatePrivate(spec);
        } catch (final Exception e) {
            throw new CryptoException("Erro ao construir PrivateKey", e);
        }
    }

}
