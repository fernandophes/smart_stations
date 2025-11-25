package br.edu.ufersa.cc.seg.edge;

import java.io.IOException;
import java.net.InetAddress;

import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.factories.EnvOrInputFactory;
import br.edu.ufersa.cc.seg.common.factories.MessageFactory;
import br.edu.ufersa.cc.seg.common.network.Message;
import br.edu.ufersa.cc.seg.common.network.Messenger;
import br.edu.ufersa.cc.seg.common.network.ServerMessenger;
import br.edu.ufersa.cc.seg.common.network.UdpMessenger;
import br.edu.ufersa.cc.seg.common.network.UdpServerMessenger;
import br.edu.ufersa.cc.seg.common.utils.Fields;
import br.edu.ufersa.cc.seg.common.utils.MessageType;
import br.edu.ufersa.cc.seg.common.utils.ServerType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EdgeServer {

    private final CryptoService cryptoService;
    private final EnvOrInputFactory envOrInputFactory;

    private final ServerMessenger serverMessenger;
    private Messenger locationMessenger;
    private Messenger datacenterMessenger;

    public EdgeServer(final CryptoService cryptoService, final EnvOrInputFactory envOrInputFactory)
            throws IOException {
        this.cryptoService = cryptoService;
        this.envOrInputFactory = envOrInputFactory;
        this.serverMessenger = new UdpServerMessenger(cryptoService);
    }

    public void start() {
        connectToLocationServer();
        register();
        locateDatacenterServer();
        serverMessenger.subscribe(this::handleRequest);
    }

    @SneakyThrows
    public void stop() {
        datacenterMessenger.close();
        locationMessenger.close();
        serverMessenger.close();
    }

    @SneakyThrows
    private void connectToLocationServer() {
        final var locationHost = envOrInputFactory.getString("LOCATION_HOST");
        final var locationPort = envOrInputFactory.getInt("LOCATION_PORT");

        locationMessenger = new UdpMessenger(locationHost, locationPort, cryptoService);
    }

    @SneakyThrows
    private void register() {
        log.info("Registrando-se no servidor de localização...");
        final var request = new Message(MessageType.REGISTER_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.EDGE)
                .withValue(Fields.HOST, InetAddress.getLocalHost().getHostAddress())
                .withValue(Fields.PORT, serverMessenger.getPort());

        locationMessenger.send(request);

        final var response = locationMessenger.receive();

        if (response.getType().equals(MessageType.ERROR)) {
            log.error("Erro ao registrar servidor de borda: {}", response.getValues());
        } else {
            log.info("Registrado no servidor de localização: {}");
        }
    }

    @SneakyThrows
    private void locateDatacenterServer() {
        final var request = new Message(MessageType.LOCATE_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.DATACENTER);

        locationMessenger.send(request);
        final var response = locationMessenger.receive();

        if (response.getType().equals(MessageType.OK)) {
            final var host = (String) response.getValues().get(Fields.HOST);
            final var port = (int) response.getValues().get(Fields.PORT);

            datacenterMessenger = new UdpMessenger(host, port, cryptoService);
        }
    }

    private Message handleRequest(final Message request) {
        log.info("Leitura recebida: {}", request.getValues());

        request.setType(MessageType.STORE_SNAPSHOT);
        datacenterMessenger.send(request);

        return MessageFactory.ok();
    }

}
