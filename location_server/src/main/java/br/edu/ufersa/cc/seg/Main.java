package br.edu.ufersa.cc.seg;

import java.util.Optional;
import java.util.Scanner;

import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.location.LocationServer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {

    public static void main(String[] args) throws Exception {
        log.info("Iniciando servidor de localização...");

        final var scanner = new Scanner(System.in);

        // Obter chaves
        final var encriptionKey = Optional.ofNullable(System.getenv("ENCRIPTION_KEY"))
                .orElseGet(() -> {
                    System.out.print("Encription key: ");
                    return scanner.nextLine();
                });
        final var hmacKey = Optional.ofNullable(System.getenv("HMAC_KEY"))
                .orElseGet(() -> {
                    System.out.print("HMAC key: ");
                    return scanner.nextLine();
                });

        // Instanciar serviço de criptografia
        final var cryptoService = new CryptoService(encriptionKey, hmacKey);

        final var locationServer = new LocationServer(8484, cryptoService);
        locationServer.start();

        scanner.close();
    }

}