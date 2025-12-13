package br.edu.ufersa.cc.seg.gateway;

import java.io.IOException;
import java.net.InetAddress;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

import br.edu.ufersa.cc.seg.FilterFirewall;
import br.edu.ufersa.cc.seg.common.utils.ConnectionType;
import io.javalin.http.UnauthorizedResponse;
import lombok.Getter;
import lombok.SneakyThrows;

public class MyHttpClient {

    private final FilterFirewall firewall;

    private final HttpClient httpClient = HttpClients.createDefault();
    private final String uri;

    @Getter
    private final InetAddress host;

    @Getter
    private final int port;

    @SneakyThrows
    public MyHttpClient(final String host, final int port, final FilterFirewall firewall) {
        this.host = InetAddress.getByName(host);
        this.port = port;
        this.uri = "http://" + host + ":" + port;
        this.firewall = firewall;
    }

    public ConnectionType getConnectionType() {
        return ConnectionType.HTTP;
    }

    @SneakyThrows
    public HttpResponse useSymmetric(final String token) {
        final var request = new HttpGet(uri + "/api/use-symmetric");
        request.addHeader("token", token);
        return execute(request);
    }

    @SneakyThrows
    public HttpResponse acceptGateway() {
        final var request = new HttpGet(uri + "/api/accept-gateway");
        return execute(request);
    }

    @SneakyThrows
    public HttpResponse getSnapshotsAfter(final String token, final String timestamp) {
        final var request = new HttpGet(uri + "/api/snapshots/" + timestamp);
        request.addHeader("token", token);
        return execute(request);
    }

    private HttpResponse execute(final HttpGet request) throws IOException, ClientProtocolException {
        if (firewall.isAllowed(getConnectionType(), host, port)) {
            return httpClient.execute(request);
        } else {
            throw new UnauthorizedResponse("Não autorizado");
        }
    }

}
