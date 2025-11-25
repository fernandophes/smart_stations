package br.edu.ufersa.cc.seg.edge;

import java.io.IOException;
import java.net.InetAddress;

import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.factories.EnvOrInputFactory;
import br.edu.ufersa.cc.seg.common.factories.MessageFactory;
import br.edu.ufersa.cc.seg.common.network.Message;
import br.edu.ufersa.cc.seg.common.network.UdpClientMessenger;
import br.edu.ufersa.cc.seg.common.network.UdpServerMessenger;
import br.edu.ufersa.cc.seg.common.utils.Fields;
import br.edu.ufersa.cc.seg.common.utils.MessageType;
import br.edu.ufersa.cc.seg.common.utils.ServerType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EdgeServer {

    // private final int port;
    private final CryptoService cryptoService;
    private final EnvOrInputFactory envOrInputFactory;

    private final UdpServerMessenger messenger;

    public EdgeServer(final CryptoService cryptoService, final EnvOrInputFactory envOrInputFactory)
            throws IOException {
        // this.port = port;
        this.cryptoService = cryptoService;
        this.envOrInputFactory = envOrInputFactory;
        this.messenger = new UdpServerMessenger(cryptoService);
    }

    public void start() {
        register();
        messenger.subscribe(this::handleRequest);
    }

    @SneakyThrows
    public void stop() {
        messenger.close();
    }

    @SneakyThrows
    private void register() {
        log.info("Registrando-se no servidor de localização...");
        final var request = new Message(MessageType.REGISTER_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.EDGE)
                .withValue(Fields.HOST, InetAddress.getLocalHost().getHostAddress())
                .withValue(Fields.PORT, messenger.getPort());

        final var locationHost = envOrInputFactory.getString("LOCATION_HOST");
        final var locationPort = envOrInputFactory.getInt("LOCATION_PORT");

        final var locationMessenger = new UdpClientMessenger(locationHost, locationPort, cryptoService);
        locationMessenger.send(request);

        final var response = locationMessenger.receive();

        if (response.getType().equals(MessageType.ERROR)) {
            log.error("Erro ao registrar servidor de borda: {}", response.getValues());
        } else {
            log.info("Registrado no servidor de localização: {}");
        }

        locationMessenger.close();
    }

    private Message handleRequest(final Message request) {
        log.info("Leitura recebida: {}", request.getValues());
        return MessageFactory.ok();
    }

}
