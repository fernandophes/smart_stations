package br.edu.ufersa.cc.seg.auth;

import br.edu.ufersa.cc.seg.auth.exceptions.AuthFailureException;
import br.edu.ufersa.cc.seg.auth.services.AuthService;
import br.edu.ufersa.cc.seg.common.factories.EnvOrInputFactory;
import br.edu.ufersa.cc.seg.common.factories.MessageFactory;
import br.edu.ufersa.cc.seg.common.factories.MessengerFactory;
import br.edu.ufersa.cc.seg.common.factories.ServerMessengerFactory;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.Messenger;
import br.edu.ufersa.cc.seg.common.messengers.ServerMessenger;
import br.edu.ufersa.cc.seg.common.utils.MessageType;
import lombok.SneakyThrows;

public class AuthServer {

    private final EnvOrInputFactory envOrInputFactory;

    private final AuthService service = new AuthService();
    private final ServerMessenger serverMessenger = ServerMessengerFactory.tcp();
    private Messenger locationMessenger;

    public AuthServer(final EnvOrInputFactory envOrInputFactory) {
        this.envOrInputFactory = envOrInputFactory;
    }

    public void start() {
        serverMessenger.subscribe(this::handleRequest);
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
    private void connectToLocationServer() {
        final var locationHost = envOrInputFactory.getString("LOCATION_HOST");
        final var locationPort = envOrInputFactory.getInt("LOCATION_PORT");

        locationMessenger = MessengerFactory.udp(locationHost, locationPort);
    }

}
