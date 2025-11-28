package br.edu.ufersa.cc.seg.datacenter;

import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.factories.EnvOrInputFactory;
import br.edu.ufersa.cc.seg.common.factories.MessageFactory;
import br.edu.ufersa.cc.seg.common.factories.MessengerFactory;
import br.edu.ufersa.cc.seg.common.factories.ServerMessengerFactory;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.SecureMessenger;
import br.edu.ufersa.cc.seg.common.messengers.ServerMessenger;
import br.edu.ufersa.cc.seg.common.utils.Constants;
import br.edu.ufersa.cc.seg.common.utils.Element;
import br.edu.ufersa.cc.seg.common.utils.Fields;
import br.edu.ufersa.cc.seg.common.utils.MessageType;
import br.edu.ufersa.cc.seg.common.utils.ServerType;
import br.edu.ufersa.cc.seg.datacenter.entities.Snapshot;
import br.edu.ufersa.cc.seg.datacenter.services.SnapshotService;
import io.javalin.Javalin;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Datacenter {

    private final CryptoService cryptoService;
    private final EnvOrInputFactory envOrInputFactory;

    private final Javalin httpServer;
    private final ServerMessenger serverMessenger;
    private SecureMessenger locationMessenger;

    /*
     * Serviço do banco de dados
     */
    private final SnapshotService snapshotService = new SnapshotService();

    public Datacenter(final CryptoService cryptoService, final EnvOrInputFactory envOrInputFactory)
            throws IOException {
        this.cryptoService = cryptoService;
        this.envOrInputFactory = envOrInputFactory;
        this.serverMessenger = ServerMessengerFactory.secureTcp(cryptoService);
        httpServer = Javalin.create();
    }

    public void start() {
        connectToLocationServer();
        serverMessenger.subscribe(this::handleRequest);
        configureHttpServer();
        register();
    }

    @SneakyThrows
    public void stop() {
        locationMessenger.close();
        serverMessenger.close();
        httpServer.stop();
    }

    @SneakyThrows
    private void connectToLocationServer() {
        final var locationHost = envOrInputFactory.getString("LOCATION_HOST");
        final var locationPort = envOrInputFactory.getInt("LOCATION_PORT");

        locationMessenger = MessengerFactory.secureUdp(locationHost, locationPort, cryptoService);
    }

    @SneakyThrows
    private void register() {
        log.info("Registrando-se no servidor de localização...");
        final var request = new Message(MessageType.REGISTER_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.DATACENTER)
                .withValue(Fields.HOST, InetAddress.getLocalHost().getHostAddress())
                .withValue(Fields.PORT, serverMessenger.getPort());

        locationMessenger.send(request);

        final var response = locationMessenger.receive();

        if (response.getType().equals(MessageType.ERROR)) {
            log.error("Erro ao registrar datacenter: {}", response.getValues());
        } else {
            log.info("Registrado no servidor de localização: {}");
        }
    }

    private void configureHttpServer() {
        httpServer
                .get("/api/snapshots", ctx -> {
                    log.info("Requisição HTTP recebida");
                    ctx.json(snapshotService.listAll());
                })
                .get("/api/snapshots/{starting}", ctx -> {
                    log.info("Requisição HTTP recebida");
                    final var formattedTimestamp = ctx.pathParam("starting");
                    final var timestamp = LocalDateTime.parse(formattedTimestamp,
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    ctx.json(snapshotService.listAllAfter(timestamp));
                })
                .start(8480);

        log.info("Servidor HTTP iniciado na porta {}", httpServer.port());
    }

    private Message handleRequest(final Message request) {
        if (MessageType.STORE_SNAPSHOT.equals(request.getType())) {
            storeSnapshot(request);
            return MessageFactory.ok();
        } else {
            return MessageFactory.error("Tipo de mensagem não suportada");
        }

    }

    private void storeSnapshot(final Message request) {
        final var values = request.getValues();
        final var deviceName = (String) values.get("deviceName");
        final var formattedTimestamp = (String) values.get("timestamp");
        final var timestamp = LocalDateTime.parse(formattedTimestamp, Constants.DATE_TIME_FORMATTER);

        log.info("Armazenando as leituras de {} em {}...", deviceName, formattedTimestamp);

        for (final var element : Element.values()) {
            Optional.of((double) values.get(element.name()))
                    .ifPresent(value -> {
                        final var snapshot = new Snapshot()
                                .setDeviceName(deviceName)
                                .setTimestamp(timestamp)
                                .setElement(element)
                                .setCapturedValue(value);

                        snapshotService.create(snapshot);
                    });
        }

        log.info("Leituras de {} em {} armazenadas", deviceName, formattedTimestamp);
    }

}
