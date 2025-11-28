package br.edu.ufersa.cc.seg.common.factories;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;
import java.util.Scanner;

import br.edu.ufersa.cc.seg.common.crypto.AESService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EnvOrInputFactory implements Closeable {

    private final Scanner scanner;

    public String getString(final String key) {
        return Optional.ofNullable(System.getenv(key))
                .orElseGet(() -> {
                    System.out.print(key + ": ");
                    return scanner.nextLine();
                });
    }

    public int getInt(final String key) {
        return Optional.ofNullable(System.getenv(key))
                .map(Integer::parseInt)
                .orElseGet(() -> {
                    System.out.print(key + ": ");

                    final var value = scanner.nextInt();
                    scanner.nextLine();
                    return value;
                });
    }

    public String encriptionKey() {
        return getString("ENCRIPTION_KEY");
    }

    public String hmacKey() {
        return getString("HMAC_KEY");
    }

    public int port() {
        return getInt("PORT");
    }

    public AESService cryptoService() {
        // Obter chaves
        final var encriptionKey = encriptionKey();
        final var hmacKey = hmacKey();

        // Instanciar serviço de criptografia
        return new AESService(encriptionKey, hmacKey);
    }

    @Override
    public void close() throws IOException {
        scanner.close();
    }

}
