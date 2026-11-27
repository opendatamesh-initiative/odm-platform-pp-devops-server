package org.opendatamesh.platform.pp.devops.utils.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientException;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientTransportException;
import org.opendatamesh.platform.pp.devops.utils.client.http.HttpEntity;
import org.opendatamesh.platform.pp.devops.utils.client.http.HttpMethod;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientRequestFailedException;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientUnreadableResponseException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.UnknownContentTypeException;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Which failures count as "no HTTP response" (ClientTransportException) and which keep the HTTP status. The
 * RestTemplate comes from RestTemplateBuilder, as in production (Apache HttpComponents on the classpath).
 */
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

    @Test
    void whenJsonAnswerCannotBeParsedThenUnreadableNotHttp500() throws IOException {
        String url = serve(200, "application/json", "{\"providerRunId\": ");

        ClientException failure = catchThrowableOfType(ClientException.class, () -> post(url));

        assertThat(failure).isInstanceOf(ClientUnreadableResponseException.class);
        assertThat(failure.getMessage()).startsWith("unreadable answer: content type application/json, ");
        assertThat(failure.getResponseBody()).doesNotContain("class org.").doesNotContain("JsonNode");
    }

    @Test
    void whenUnknownContentTypeIsNullThenNoNullInMessage() {
        UnknownContentTypeException unknown = new UnknownContentTypeException(JsonNode.class, null, HttpStatus.OK, "OK",
                new HttpHeaders(), "plain words".getBytes(StandardCharsets.UTF_8));

        ClientException failure = RestTemplateWrapper.translate(unknown);

        assertThat(failure.getCode()).isEqualTo(200);
        assertThat(failure.getResponseBody()).isEqualTo("Unreadable response: plain words");
    }

    @Test
    void whenRequestBodyCannotBeWrittenThenRequestFailedNotUnreadableAnswer() {
        ClientException failure = RestTemplateWrapper.translate(new RestClientException("No HttpMessageConverter for java.lang.Object"));

        assertThat(failure).isInstanceOf(ClientRequestFailedException.class);
        assertThat(failure).isNotInstanceOf(ClientUnreadableResponseException.class);
    }

    @Test
    void whenConnectionClosedWithoutAnswerThenTransportFailureAfterSending() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            Thread closer = new Thread(() -> {
                try (Socket client = socket.accept()) {
                    client.getInputStream().read(new byte[1024]);
                } catch (IOException ignored) {
                    // the test only needs the connection closed without an answer
                }
            });
            closer.start();

            ClientException failure = catchThrowableOfType(ClientException.class,
                    () -> post("http://localhost:" + socket.getLocalPort() + "/start"));

            closer.join(5000);
            assertThat(failure).isInstanceOf(ClientTransportException.class);
            assertThat(failure).hasRootCauseInstanceOf(NoHttpResponseException.class);
        }
    }

    @Test
    void whenNoAnswerInTimeThenTransportFailureWithReadTimeout() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/start", exchange -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();

        ClientException failure = catchThrowableOfType(ClientException.class,
                () -> post("http://localhost:" + server.getAddress().getPort() + "/start", Duration.ofMillis(300)));

        assertThat(failure).isInstanceOf(ClientTransportException.class);
        assertThat(failure).hasRootCauseInstanceOf(SocketTimeoutException.class);
        assertThat(failure.getMessage()).startsWith("no HTTP response:");
    }

    private JsonNode post(String url) {
        return post(url, Duration.ofSeconds(2));
    }

    private JsonNode post(String url, Duration readTimeout) {
        RestTemplate restTemplate = new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(readTimeout)
                .build();
        return RestTemplateWrapper.wrap(restTemplate)
                .exchange(url, HttpMethod.POST, new HttpEntity<>("{}", List.of()), JsonNode.class);
    }

    private String serve(int status, String contentType, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/start", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            if (contentType != null) {
                exchange.getResponseHeaders().add("Content-Type", contentType);
            }
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/start";
    }
}
