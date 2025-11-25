package br.edu.ufersa.cc.seg.common.network;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import br.edu.ufersa.cc.seg.common.crypto.CryptoService;
import br.edu.ufersa.cc.seg.common.crypto.SecureMessage;
import lombok.SneakyThrows;

public class TcpMessenger extends Messenger {

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    public TcpMessenger(final Socket socket, final CryptoService cryptoService) throws IOException {
        super(cryptoService);

        this.socket = socket;
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    public TcpMessenger(final String host, final int port, final CryptoService cryptoService) throws IOException {
        this(new Socket(host, port), cryptoService);
    }

    public TcpMessenger(final ServerSocket serverSocket, final CryptoService cryptoService) throws IOException {
        this(serverSocket.accept(), cryptoService);
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    @Override
    @SneakyThrows
    public void send(final Message message) {
        final var secureMsg = cryptoService.encrypt(message.toBytes());
        out.writeObject(secureMsg);
        out.flush();
    }

    @Override
    @SneakyThrows
    public Message receive() {
        final var secureMsg = (SecureMessage) in.readObject();
        final var messageAsBytes = cryptoService.decrypt(secureMsg);

        return Message.fromBytes(messageAsBytes);
    }

    @Override
    public void close() throws IOException {
        in.close();
        out.close();
        socket.close();
    }

}
