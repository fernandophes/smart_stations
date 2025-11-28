package br.edu.ufersa.cc.seg;

import java.util.Optional;
import java.util.Scanner;

import br.edu.ufersa.cc.seg.common.factories.EnvOrInputFactory;
import br.edu.ufersa.cc.seg.location.LocationServer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {

    public static void main(String[] args) throws Exception {
        log.info("Iniciando servidor de localização...");

        final var input = Optional.ofNullable(Main.class.getResourceAsStream("/env.txt"))
                .orElse(System.in);

        final var factory = new EnvOrInputFactory(new Scanner(input));
        final var server = new LocationServer(factory.port());

        server.start();
        factory.close();
    }

}
