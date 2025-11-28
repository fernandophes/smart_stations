package br.edu.ufersa.cc.seg;

import java.io.IOException;
import java.util.Optional;
import java.util.Scanner;

import br.edu.ufersa.cc.seg.common.factories.EnvOrInputFactory;
import br.edu.ufersa.cc.seg.device.Device;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {

    public static void main(String[] args) throws IOException {
        log.info("Iniciando servidor de borda...");

        final var input = Optional.ofNullable(Main.class.getResourceAsStream("/env.txt"))
                .orElse(System.in);

        final var factory = new EnvOrInputFactory(new Scanner(input));
        final var name = factory.getString("DEVICE_NAME");
        final var device = new Device(name, factory.aesService(), factory);

        device.start();
        factory.close();
    }

}
