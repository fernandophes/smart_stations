package br.edu.ufersa.cc.seg.common.concrete_crypto;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

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

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String CIPHER_ALGORITHM = "RSA";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int IV_SIZE = 16;

    private final SecretKey encryptionKey;
    private final SecretKey hmacKey;

    public RSAService(final byte[] encryptionKey, final byte[] hmacKey) {
        this.encryptionKey = new SecretKeySpec(encryptionKey, CIPHER_ALGORITHM);
        this.hmacKey = new SecretKeySpec(encryptionKey, HMAC_ALGORITHM);
    }

    public RSAService(final String encryptionKey, final String hmacKey) {
        this(Base64.getDecoder().decode(encryptionKey), Base64.getDecoder().decode(hmacKey));
    }

    @Override
    @SneakyThrows
    public SecureMessage encrypt(final byte[] message) {
        log.debug("Criptografando mensagem...\n{}", new String(message));

        // Gerar IV aleatório
        final var iv = new byte[IV_SIZE];
        SECURE_RANDOM.nextBytes(iv);
        final var ivSpec = new IvParameterSpec(iv);

        // Cifrar a mensagem
        final var cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, ivSpec);
        final var encrypted = cipher.doFinal(message);

        // Gera HMAC (encrypted + iv + timestamp para evitar replay)
        final var timestamp = System.currentTimeMillis();
        final var hmac = generateHmac(encrypted, iv, timestamp);

        // Retorna mensagem segura
        final var secureMessage = SecureMessage.builder()
                .encryptedContent(encrypted)
                .hmac(hmac)
                .iv(iv)
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

        // Valida HMAC primeiro
        final var expectedHmac = generateHmac(
                secureMessage.getEncryptedContent(),
                secureMessage.getIv(),
                secureMessage.getTimestamp());

        if (!MessageDigest.isEqual(expectedHmac, secureMessage.getHmac())) {
            throw new CryptoException("HMAC inválido - mensagem pode ter sido adulterada");
        }

        // Se HMAC ok, decifra
        final var cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new IvParameterSpec(secureMessage.getIv()));
        final var original = cipher.doFinal(secureMessage.getEncryptedContent());
        log.debug("Mensagem descriptografada:\n{}", new String(original));

        return original;
    }

    /**
     * Gera HMAC para os componentes da mensagem
     */
    private byte[] generateHmac(final byte[] encrypted, final byte[] iv, final long timestamp) {
        try {
            final var mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hmacKey);

            // HMAC(encrypted + iv + timestamp)
            mac.update(encrypted);
            mac.update(iv);
            mac.update(longToBytes(timestamp));

            return mac.doFinal();

        } catch (final Exception e) {
            log.error("Erro ao gerar HMAC", e);
            throw new CryptoException("Erro ao gerar HMAC", e);
        }
    }

    /**
     * Converte long para array de bytes
     */
    private static byte[] longToBytes(long x) {
        final var result = new byte[8];
        for (var i = 7; i >= 0; i--) {
            result[i] = (byte) (x & 0xFF);
            x >>= 8;
        }
        return result;
    }

}
