package br.edu.ufersa.cc.seg.gateway;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

import lombok.SneakyThrows;

public class MyHttpClient {

    private final HttpClient httpClient = HttpClients.createDefault();
    private final String uri;

    public MyHttpClient(final String host, final int port) {
        this.uri = "http://" + host + ":" + port;
    }

    @SneakyThrows
    public HttpResponse useSymmetric(final String token) {
        final var request = new HttpGet(uri + "/api/use-symmetric");
        request.addHeader("token", token);
        return httpClient.execute(request);
    }

    @SneakyThrows
    public HttpResponse acceptGateway() {
        final var request = new HttpGet(uri + "/api/accept-gateway");
        return httpClient.execute(request);
    }

    @SneakyThrows
    public HttpResponse getSnapshotsAfter(final String token, final String timestamp) {
        final var request = new HttpGet(uri + "/api/snapshots/" + timestamp);
        request.addHeader("token", token);
        return httpClient.execute(request);
    }

}
