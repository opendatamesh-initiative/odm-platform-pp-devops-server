package org.opendatamesh.platform.pp.devops.client.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskRunCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartCommandRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ExecutorParametersRes;
import org.opendatamesh.platform.pp.devops.utils.client.RestUtilsFactory;
import org.opendatamesh.platform.pp.devops.utils.client.http.HttpHeader;
import org.springframework.web.client.RestTemplate;

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
     * Scenario: The client calls the executor v2 API with POST and the cached secret headers
     *   Given executor "starter" at address A and the secret x-odm-token cached for an activity
     *   When the client starts a run, reads its status, reads its logs, and cancels it
     *   Then it posts to A/api/v2/up/executor/tasks/start
     *   And it posts to A/api/v2/up/executor/tasks/status and /logs with the run command in the body
     *   And it posts to A/api/v2/up/executor/tasks/cancel with the run command in the body
     *   And the run command carries executorParameters and providerRunId
     *   And no call has a query string
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
            ExecutorParametersRes executorParameters = new ExecutorParametersRes();
            executorParameters.setRepositoryKey("repo-key");
            String providerRunId = "run 1";
            ExecutorTaskRunCommandRes runCommand = new ExecutorTaskRunCommandRes(executorParameters, providerRunId);
            client.startTask(new ExecutorTaskStartCommandRes());
            client.getTaskStatus(runCommand);
            client.getTaskLogs(runCommand);
            client.cancelTask(runCommand);

            assertThat(requests).hasSize(4);
            assertThat(requests).allSatisfy(request -> assertThat(request.method()).isEqualTo("POST"));
            assertThat(requests).allSatisfy(request -> assertThat(request.query()).isNull());
            assertThat(requests).allSatisfy(request -> assertThat(request.token()).isEqualTo("cached-secret"));

            assertThat(requests.get(0).path()).isEqualTo("/api/v2/up/executor/tasks/start");

            assertThat(requests.get(1).path()).isEqualTo("/api/v2/up/executor/tasks/status");
            assertRunCommandBody(requests.get(1).body(), providerRunId);

            assertThat(requests.get(2).path()).isEqualTo("/api/v2/up/executor/tasks/logs");
            assertRunCommandBody(requests.get(2).body(), providerRunId);

            assertThat(requests.get(3).path()).isEqualTo("/api/v2/up/executor/tasks/cancel");
            assertRunCommandBody(requests.get(3).body(), providerRunId);
        } finally {
            server.stop(0);
        }
    }

    private static void assertRunCommandBody(String body, String providerRunId) throws Exception {
        JsonNode json = OBJECT_MAPPER.readTree(body);
        assertThat(json.get("providerRunId").asText()).isEqualTo(providerRunId);
        assertThat(json.get("executorParameters").get("repositoryKey").asText()).isEqualTo("repo-key");
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
