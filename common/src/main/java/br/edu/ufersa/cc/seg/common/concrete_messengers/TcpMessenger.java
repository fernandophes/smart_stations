package br.edu.ufersa.cc.seg.common.concrete_messengers;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.Messenger;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TcpMessenger extends Messenger {

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    public TcpMessenger(final Socket socket) throws IOException {
        this.socket = socket;
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    public TcpMessenger(final String host, final int port) throws IOException {
        this(new Socket(host, port));
    }

    public TcpMessenger(final ServerSocket serverSocket) throws IOException {
        this(serverSocket.accept());
    }

    @Override
    public int getPort() {
        return socket.getLocalPort();
    }

    @Override
    @SneakyThrows
    public void send(final Message message) {
        out.writeObject(message);
        out.flush();

        log.info("Enviando mensagem do tipo {} para {}/{}:{}", message.getType(), socket.getInetAddress().getHostName(),
                socket.getInetAddress().getHostAddress(), socket.getPort());
    }

    @Override
    @SneakyThrows
    public Message receive() {
        final var message = (Message) in.readObject();
        log.info("Recebida mensagem do tipo {} para {}/{}:{}", message.getType(), socket.getInetAddress().getHostName(),
                socket.getInetAddress().getHostAddress(), socket.getPort());

        return message;
    }

    @Override
    public void close() throws IOException {
        closeSubscriptions();
        in.close();
        out.close();
        socket.close();
    }

}
