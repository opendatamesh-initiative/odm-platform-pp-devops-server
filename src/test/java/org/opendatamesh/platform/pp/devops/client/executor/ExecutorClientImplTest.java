package org.opendatamesh.platform.pp.devops.client.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartCommandRes;
import org.opendatamesh.platform.pp.devops.utils.client.RestUtilsFactory;
import org.opendatamesh.platform.pp.devops.utils.client.http.HttpHeader;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executor v2 HTTP client against a real socket.
 * Scenarios trace to {@code spdd/prompt/BDMD-5437-202609290955-[Feat]-service-full-control-happy-path.md}.
 */
class ExecutorClientImplTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Feature: Executor client
     *
     * Scenario: The client calls the executor v2 API with the cached secret headers
     *   Given executor "starter" at address A and the secret x-odm-token cached for an activity
     *   When the client starts a run, reads its status, reads its logs, and cancels it
     *   Then it posts to A/api/v2/up/executor/tasks/start
     *   And it gets A/api/v2/up/executor/tasks/status and /logs with providerRunId query-encoded
     *   And cancel posts JSON providerRunId
     *   And every call carries the header x-odm-token
     */
    @Test
    void whenCallingExecutorThenV2PathsAndSecretHeaders() throws Exception {
        List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            requests.add(new RecordedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getRawPath(),
                    exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders().getFirst("x-odm-token"),
                    new String(body, StandardCharsets.UTF_8)
            ));
            byte[] response = responseBody(exchange.getRequestURI().getRawPath());
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            String address = "http://127.0.0.1:" + server.getAddress().getPort();
            ExecutorClientImpl client = new ExecutorClientImpl(
                    address,
                    List.of(new HttpHeader("x-odm-token", "cached-secret")),
                    RestUtilsFactory.getRestUtils(new RestTemplate())
            );
            String providerRunId = "run 1";
            client.startTask(new ExecutorTaskStartCommandRes());
            client.getTaskStatus(providerRunId);
            client.getTaskLogs(providerRunId);
            client.cancelTask(providerRunId);

            assertThat(requests).hasSize(4);
            assertThat(requests).allSatisfy(request -> assertThat(request.token()).isEqualTo("cached-secret"));

            assertThat(requests.get(0).method()).isEqualTo("POST");
            assertThat(requests.get(0).path()).isEqualTo("/api/v2/up/executor/tasks/start");

            String encodedRunId = UriUtils.encodeQueryParam(providerRunId, StandardCharsets.UTF_8);
            assertThat(requests.get(1).method()).isEqualTo("GET");
            assertThat(requests.get(1).path()).isEqualTo("/api/v2/up/executor/tasks/status");
            assertThat(requests.get(1).query()).isEqualTo("providerRunId=" + encodedRunId);
            assertThat(requests.get(1).query()).doesNotContain(" ");

            assertThat(requests.get(2).method()).isEqualTo("GET");
            assertThat(requests.get(2).path()).isEqualTo("/api/v2/up/executor/tasks/logs");
            assertThat(requests.get(2).query()).isEqualTo("providerRunId=" + encodedRunId);

            assertThat(requests.get(3).method()).isEqualTo("POST");
            assertThat(requests.get(3).path()).isEqualTo("/api/v2/up/executor/tasks/cancel");
            JsonNode cancelBody = OBJECT_MAPPER.readTree(requests.get(3).body());
            assertThat(cancelBody.get("providerRunId").asText()).isEqualTo(providerRunId);
        } finally {
            server.stop(0);
        }
    }

    private static byte[] responseBody(String path) {
        if (path.endsWith("/start")) {
            return "{\"providerRunId\":\"run 1\"}".getBytes(StandardCharsets.UTF_8);
        }
        if (path.endsWith("/status")) {
            return "{\"providerRunId\":\"run 1\",\"status\":\"SUCCEEDED\"}".getBytes(StandardCharsets.UTF_8);
        }
        if (path.endsWith("/logs")) {
            return "{\"content\":\"log line\"}".getBytes(StandardCharsets.UTF_8);
        }
        return "{}".getBytes(StandardCharsets.UTF_8);
    }

    private record RecordedRequest(String method, String path, String query, String token, String body) {
    }
}
