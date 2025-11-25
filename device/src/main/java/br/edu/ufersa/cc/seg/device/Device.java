package br.edu.ufersa.cc.seg.device;

import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.factories.EnvOrInputFactory;
import br.edu.ufersa.cc.seg.common.factories.MessageFactory;
import br.edu.ufersa.cc.seg.common.network.Message;
import br.edu.ufersa.cc.seg.common.network.Messenger;
import br.edu.ufersa.cc.seg.common.network.UdpClientMessenger;
import br.edu.ufersa.cc.seg.common.utils.Fields;
import br.edu.ufersa.cc.seg.common.utils.MessageType;
import br.edu.ufersa.cc.seg.common.utils.ServerType;
import lombok.Data;
import lombok.SneakyThrows;

@Data
public class Device {

    private final String name;
    private final CryptoService cryptoService;
    private final EnvOrInputFactory envOrInputFactory;

    private Messenger locationMessenger;
    private Messenger edgeMessenger;

    public void start() {
        connectLocationServer();
        locateEdgeServer();

        final var helloWorld = MessageFactory.ok("Hello", "World")
                .withValue("deviceName", name);
        edgeMessenger.send(helloWorld);
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

}
