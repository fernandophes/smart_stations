package br.edu.ufersa.cc.seg.common.concrete_messengers;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.crypto.SecureMessage;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.SecureMessenger;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SecureTcpMessenger extends SecureMessenger {

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    public SecureTcpMessenger(final Socket socket, final CryptoService cryptoService) throws IOException {
        super(cryptoService);

        this.socket = socket;
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    public SecureTcpMessenger(final String host, final int port, final CryptoService cryptoService) throws IOException {
        this(new Socket(host, port), cryptoService);
    }

    public SecureTcpMessenger(final ServerSocket serverSocket, final CryptoService cryptoService) throws IOException {
        this(serverSocket.accept(), cryptoService);
    }

    @Override
    public InetAddress getHost() {
        return socket.getInetAddress();
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    @Override
    public boolean isClosed() {
        return socket.isClosed();
    }

    @Override
    @SneakyThrows
    public void send(final Message message) {
        final var secureMessage = cryptoService.encrypt(message.toBytes());
        out.writeObject(secureMessage);
        out.flush();

        log.info("Enviando mensagem do tipo {} para {}/{}:{}", message.getType(), socket.getInetAddress().getHostName(),
                socket.getInetAddress().getHostAddress(), socket.getPort());
    }

    @Override
    @SneakyThrows
    public Message receive() {
        final var secureMessage = (SecureMessage) in.readObject();
        final var messageAsBytes = cryptoService.decrypt(secureMessage);
        final var message = Message.fromBytes(messageAsBytes);

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
