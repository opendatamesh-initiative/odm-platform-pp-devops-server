package org.opendatamesh.platform.pp.devops.utils.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientException;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientTransportException;
import org.opendatamesh.platform.pp.devops.utils.client.http.HttpEntity;
import org.opendatamesh.platform.pp.devops.utils.client.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Which failures count as "no HTTP response" (ClientTransportException) and which keep the HTTP status. */
class RestTemplateWrapperTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void whenNothingListensThenTransportFailure() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        ClientException failure = catchThrowableOfType(ClientException.class, () -> post("http://localhost:" + port + "/start"));

        assertThat(failure).isInstanceOf(ClientTransportException.class);
        assertThat(failure.getCode()).isEqualTo(500);
    }

    @Test
    void whenAnswerCannotBeReadThenHttpStatusIsKept() throws IOException {
        String url = serve(200, "text/html", "<html>login</html>");

        ClientException failure = catchThrowableOfType(ClientException.class, () -> post(url));

        assertThat(failure).isNotInstanceOf(ClientTransportException.class);
        assertThat(failure.getCode()).isEqualTo(200);
        assertThat(failure.getResponseBody()).contains("text/html").contains("<html>login</html>");
    }

    @Test
    void whenErrorStatusThenHttpStatusAndBodyAreKept() throws IOException {
        String url = serve(400, "text/plain", "Unknown environment prod");

        ClientException failure = catchThrowableOfType(ClientException.class, () -> post(url));

        assertThat(failure).isNotInstanceOf(ClientTransportException.class);
        assertThat(failure.getCode()).isEqualTo(400);
        assertThat(failure.getResponseBody()).isEqualTo("Unknown environment prod");
    }

    private JsonNode post(String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        return RestTemplateWrapper.wrap(new RestTemplate(factory))
                .exchange(url, HttpMethod.POST, new HttpEntity<>("{}", List.of()), JsonNode.class);
    }

    private String serve(int status, String contentType, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/start", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/start";
    }
}
