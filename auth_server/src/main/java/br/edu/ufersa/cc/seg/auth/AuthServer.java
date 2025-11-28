package br.edu.ufersa.cc.seg.auth;

import java.net.InetAddress;

import br.edu.ufersa.cc.seg.auth.dto.InstanceDto;
import br.edu.ufersa.cc.seg.auth.exceptions.AuthFailureException;
import br.edu.ufersa.cc.seg.auth.services.AuthService;
import br.edu.ufersa.cc.seg.auth.services.InstanceService;
import br.edu.ufersa.cc.seg.common.factories.EnvOrInputFactory;
import br.edu.ufersa.cc.seg.common.factories.MessageFactory;
import br.edu.ufersa.cc.seg.common.factories.MessengerFactory;
import br.edu.ufersa.cc.seg.common.factories.ServerMessengerFactory;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.Messenger;
import br.edu.ufersa.cc.seg.common.messengers.ServerMessenger;
import br.edu.ufersa.cc.seg.common.utils.Fields;
import br.edu.ufersa.cc.seg.common.utils.InstanceType;
import br.edu.ufersa.cc.seg.common.utils.MessageType;
import br.edu.ufersa.cc.seg.common.utils.ServerType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AuthServer {

    private final EnvOrInputFactory envOrInputFactory;

    private final AuthService service = new AuthService();
    private final InstanceService instanceService = new InstanceService();
    private final ServerMessenger serverMessenger = ServerMessengerFactory.tcp();
    private Messenger locationMessenger;

    public AuthServer(final EnvOrInputFactory envOrInputFactory) {
        this.envOrInputFactory = envOrInputFactory;
    }

    public void start() {
        connectToLocationServer();
        register();
        serverMessenger.subscribe(this::handleRequest);
        seed();
    }

    @SneakyThrows
    public void stop() {
        locationMessenger.close();
        serverMessenger.close();
    }

    private Message handleRequest(final Message request) {
        if (MessageType.AUTHENTICATE.equals(request.getType())) {
            final var identifier = (String) request.getValues().get("identifier");
            final var secret = (String) request.getValues().get("secret");

            try {
                final var token = service.authenticate(identifier, secret);
                return MessageFactory.ok("token", token);
            } catch (final AuthFailureException e) {
                return MessageFactory.error("Credenciais inválidas");
            }
        } else {
            return MessageFactory.error("Tipo de mensagem não suportada");
        }
    }

    @SneakyThrows
    private void register() {
        log.info("Registrando-se no servidor de localização...");
        final var request = new Message(MessageType.REGISTER_SERVER)
                .withValue(Fields.SERVER_TYPE, ServerType.AUTH)
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

    @SneakyThrows
    private void connectToLocationServer() {
        final var locationHost = envOrInputFactory.getString("LOCATION_HOST");
        final var locationPort = envOrInputFactory.getInt("LOCATION_PORT");

        locationMessenger = MessengerFactory.udp(locationHost, locationPort);
    }

    private void seed() {
        final var post = new InstanceDto().setType(InstanceType.DEVICE).setIdentifier("post-a");
        instanceService.create(post, "post123");

        final var semaphor = new InstanceDto().setType(InstanceType.DEVICE).setIdentifier("semaphor-a");
        instanceService.create(semaphor, "semaphor123");

        final var edge = new InstanceDto().setType(InstanceType.EDGE).setIdentifier("edge-a");
        instanceService.create(edge, "edge123");
    }

}
