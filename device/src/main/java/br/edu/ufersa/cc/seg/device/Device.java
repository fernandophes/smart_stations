package br.edu.ufersa.cc.seg.device;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.factories.EnvOrInputFactory;
import br.edu.ufersa.cc.seg.common.network.Message;
import br.edu.ufersa.cc.seg.common.network.Messenger;
import br.edu.ufersa.cc.seg.common.network.UdpClientMessenger;
import br.edu.ufersa.cc.seg.common.utils.Constants;
import br.edu.ufersa.cc.seg.common.utils.Fields;
import br.edu.ufersa.cc.seg.common.utils.MessageType;
import br.edu.ufersa.cc.seg.common.utils.ServerType;
import br.edu.ufersa.cc.seg.utils.ReadingType;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class Device {

    private static final Random RANDOM = new Random();
    private static final long INTERVAL = 3_000;
    private static final Timer TIMER = new Timer();

    private final String name;
    private final CryptoService cryptoService;
    private final EnvOrInputFactory envOrInputFactory;

    private Messenger locationMessenger;
    private Messenger edgeMessenger;

    private TimerTask subscription;

    private boolean isRunning;

    public void start() {
        connectLocationServer();
        locateEdgeServer();

        subscription = new TimerTask() {
            @Override
            public void run() {
                final var snapshot = simulateReading();
                log.info("Leitura feita: {}", snapshot);
                edgeMessenger.send(snapshot);
            }
        };

        TIMER.schedule(subscription, Date.from(Instant.now()), INTERVAL);
        setRunning(true);
    }

    @SneakyThrows
    private void connectLocationServer() {
        final var locationHost = envOrInputFactory.getString("LOCATION_HOST");
        final var locationPort = envOrInputFactory.getInt("LOCATION_PORT");

        locationMessenger = new UdpClientMessenger(locationHost, locationPort, cryptoService);
    }

    @SneakyThrows
    private void locateEdgeServer() {
        final var request = new Message(MessageType.LOCATE_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.EDGE);

        locationMessenger.send(request);
        final var response = locationMessenger.receive();

        if (response.getType().equals(MessageType.OK)) {
            final var host = (String) response.getValues().get(Fields.HOST);
            final var port = (int) response.getValues().get(Fields.PORT);

            edgeMessenger = new UdpClientMessenger(host, port, cryptoService);
        }
    }

    private Message simulateReading() {
        final var snapshot = new Message(MessageType.SEND_READING)
                .withValue("deviceName", getName())
                .withValue("timestamp", LocalDateTime.now().format(Constants.DATE_TIME_FORMATTER));

        for (final var type : ReadingType.values()) {
            final var raw = RANDOM.nextDouble(type.getMin(), type.getMax());
            final var scaled = BigDecimal.valueOf(raw).setScale(type.getScale(), RoundingMode.HALF_DOWN);

            snapshot.withValue(type.name(), scaled);
        }

        return snapshot;
    }

    public void close() {
        if (isRunning()) {
            subscription.cancel();
            log.info("Atividade do dispositivo {} finalizada", name);
        }
    }

}
