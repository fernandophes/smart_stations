package br.edu.ufersa.cc.seg;

import java.io.IOException;
import java.util.Optional;
import java.util.Scanner;

import br.edu.ufersa.cc.seg.auth.AuthServer;
import br.edu.ufersa.cc.seg.common.factories.EnvOrInputFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {

    public static void main(String[] args) throws IOException {
        log.info("Iniciando servidor de autenticação...");

        final var input = Optional.ofNullable(Main.class.getResourceAsStream("/env.txt"))
                .orElse(System.in);

        final var factory = new EnvOrInputFactory(new Scanner(input));
        final var server = new AuthServer(factory);

        server.start();
        factory.close();
    }

}
