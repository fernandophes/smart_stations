package br.edu.ufersa.cc.seg.datacenter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.LocalDateTime;
import java.util.Optional;

import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.factories.EnvOrInputFactory;
import br.edu.ufersa.cc.seg.common.factories.MessageFactory;
import br.edu.ufersa.cc.seg.common.network.Message;
import br.edu.ufersa.cc.seg.common.network.TcpMessenger;
import br.edu.ufersa.cc.seg.common.network.UdpClientMessenger;
import br.edu.ufersa.cc.seg.common.utils.Constants;
import br.edu.ufersa.cc.seg.common.utils.Element;
import br.edu.ufersa.cc.seg.common.utils.Fields;
import br.edu.ufersa.cc.seg.common.utils.MessageType;
import br.edu.ufersa.cc.seg.common.utils.ServerType;
import br.edu.ufersa.cc.seg.datacenter.entities.Snapshot;
import br.edu.ufersa.cc.seg.datacenter.services.SnapshotService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Datacenter {

    private final CryptoService cryptoService;
    private final EnvOrInputFactory envOrInputFactory;

    private final TcpMessenger messenger;

    /*
     * Serviço do banco de dados
     */
    private final SnapshotService snapshotService = new SnapshotService();

    public Datacenter(final CryptoService cryptoService, final EnvOrInputFactory envOrInputFactory)
            throws IOException {
        this.cryptoService = cryptoService;
        this.envOrInputFactory = envOrInputFactory;
        this.messenger = new TcpMessenger(new ServerSocket(), cryptoService);
    }

    public void start() {
        register();
        messenger.subscribe(this::handleRequest);
    }

    @SneakyThrows
    private void register() {
        log.info("Registrando-se no servidor de localização...");
        final var request = new Message(MessageType.REGISTER_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.DATACENTER)
                .withValue(Fields.HOST, InetAddress.getLocalHost().getHostAddress())
                .withValue(Fields.PORT, messenger.getPort());

        final var locationHost = envOrInputFactory.getString("LOCATION_HOST");
        final var locationPort = envOrInputFactory.getInt("LOCATION_PORT");

        final var locationMessenger = new UdpClientMessenger(locationHost, locationPort, cryptoService);
        locationMessenger.send(request);

        final var response = locationMessenger.receive();

        if (response.getType().equals(MessageType.ERROR)) {
            log.error("Erro ao registrar datacenter: {}", response.getValues());
        } else {
            log.info("Registrado no servidor de localização: {}");
        }

        locationMessenger.close();
    }

    private Message handleRequest(final Message request) {
        switch (request.getType()) {
            case SEND_SNAPSHOT:
                storeSnapshot(request);
                return MessageFactory.ok();

            default:
                break;
        }

        return MessageFactory.ok();
    }

    private void storeSnapshot(final Message request) {
        final var values = request.getValues();
        final var deviceName = (String) values.get("deviceName");
        final var formattedTimestamp = (String) values.get("timestamp");
        final var timestamp = LocalDateTime.parse(formattedTimestamp, Constants.DATE_TIME_FORMATTER);

        log.info("Armazenando as leituras de {} em {}", deviceName, formattedTimestamp);

        for (final var element : Element.values()) {
            Optional.of((double) values.get(element.name()))
                    .ifPresent(value -> {
                        final var snapshot = new Snapshot()
                                .setDeviceName(deviceName)
                                .setTimestamp(timestamp)
                                .setElement(element)
                                .setValue(value);

                        snapshotService.create(snapshot);
                    });
        }
    }

}
