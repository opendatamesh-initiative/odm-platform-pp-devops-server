package org.opendatamesh.platform.pp.devops.client.executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskCancelCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskLogsRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartResultRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStatusRes;
import org.opendatamesh.platform.pp.devops.utils.client.RestUtils;
import org.opendatamesh.platform.pp.devops.utils.client.http.HttpHeader;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Executor v2 HTTP client.
 * Scenarios trace to {@code spdd/prompt/BDMD-5437-202609290955-[Feat]-service-full-control-happy-path.md}.
 */
@ExtendWith(MockitoExtension.class)
class ExecutorClientImplTest {

    private static final String ADDRESS = "http://executor.example";

    @Mock
    private RestUtils restUtils;

    private ExecutorClientImpl client;

    @BeforeEach
    void setUp() {
        client = new ExecutorClientImpl(
                ADDRESS,
                List.of(new HttpHeader("x-odm-token", "cached-secret")),
                restUtils
        );
    }

    /**
     * Feature: Executor client
     *
     * Scenario: The client calls the executor v2 API with the cached secret headers
     *   Given executor "starter" at address A and the secret x-odm-token cached for an activity
     *   When the client starts a run, reads its status, and reads its logs
     *   Then it posts to A/api/v2/up/executor/tasks/start and gets A/api/v2/up/executor/tasks/status and /logs with providerRunId as a query parameter
     *   And every call carries the header x-odm-token
     */
    @Test
    void whenCallingExecutorThenV2PathsAndSecretHeaders() {
        when(restUtils.genericPost(any(), any(), any(), eq(ExecutorTaskStartResultRes.class)))
                .thenReturn(new ExecutorTaskStartResultRes());
        when(restUtils.genericGet(any(), any(), isNull(), eq(ExecutorTaskStatusRes.class)))
                .thenReturn(new ExecutorTaskStatusRes());
        when(restUtils.genericGet(any(), any(), isNull(), eq(ExecutorTaskLogsRes.class)))
                .thenReturn(new ExecutorTaskLogsRes());

        String providerRunId = "run 1";
        String encodedRunId = UriUtils.encodeQueryParam(providerRunId, StandardCharsets.UTF_8);
        client.startTask(new ExecutorTaskStartCommandRes());
        client.getTaskStatus(providerRunId);
        client.getTaskLogs(providerRunId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HttpHeader>> startHeaders = ArgumentCaptor.forClass(List.class);
        verify(restUtils).genericPost(
                eq(ADDRESS + "/api/v2/up/executor/tasks/start"),
                startHeaders.capture(),
                any(ExecutorTaskStartCommandRes.class),
                eq(ExecutorTaskStartResultRes.class)
        );
        assertSecret(startHeaders.getValue());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HttpHeader>> statusHeaders = ArgumentCaptor.forClass(List.class);
        verify(restUtils).genericGet(
                eq(ADDRESS + "/api/v2/up/executor/tasks/status?providerRunId=" + encodedRunId),
                statusHeaders.capture(),
                isNull(),
                eq(ExecutorTaskStatusRes.class)
        );
        assertSecret(statusHeaders.getValue());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HttpHeader>> logHeaders = ArgumentCaptor.forClass(List.class);
        verify(restUtils).genericGet(
                eq(ADDRESS + "/api/v2/up/executor/tasks/logs?providerRunId=" + encodedRunId),
                logHeaders.capture(),
                isNull(),
                eq(ExecutorTaskLogsRes.class)
        );
        assertSecret(logHeaders.getValue());
    }

    /**
     * Feature: Executor client
     *
     * Scenario: Cancel posts the provider run id and the cached secret headers
     *   Given executor "starter" at address A and the secret x-odm-token cached for an activity
     *   When the client cancels a run
     *   Then it posts to A/api/v2/up/executor/tasks/cancel with providerRunId and the header x-odm-token
     */
    @Test
    void whenCancelingThenPostsProviderRunIdAndSecretHeaders() {
        client.cancelTask("run-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HttpHeader>> headers = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<ExecutorTaskCancelCommandRes> body = ArgumentCaptor.forClass(ExecutorTaskCancelCommandRes.class);
        verify(restUtils).genericPost(
                eq(ADDRESS + "/api/v2/up/executor/tasks/cancel"),
                headers.capture(),
                body.capture(),
                eq(Object.class)
        );
        assertThat(body.getValue().getProviderRunId()).isEqualTo("run-1");
        assertSecret(headers.getValue());
    }

    private static void assertSecret(List<HttpHeader> headers) {
        assertThat(headers).anySatisfy(header -> {
            assertThat(header.getName()).isEqualTo("x-odm-token");
            assertThat(header.getValue()).isEqualTo("cached-secret");
        });
    }
}
