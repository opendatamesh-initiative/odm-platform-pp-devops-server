package org.opendatamesh.platform.pp.devops.client.executor;

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

class ExecutorClientImpl implements ExecutorClient {

    private final String address;
    private final List<HttpHeader> secretHeaders;
    private final RestUtils restUtils;

    ExecutorClientImpl(String address, List<HttpHeader> secretHeaders, RestUtils restUtils) {
        this.address = address;
        this.secretHeaders = secretHeaders;
        this.restUtils = restUtils;
    }

    @Override
    public ExecutorTaskStartResultRes startTask(ExecutorTaskStartCommandRes command) {
        return restUtils.genericPost(
                address + "/api/v2/up/executor/tasks/start",
                secretHeaders,
                command,
                ExecutorTaskStartResultRes.class
        );
    }

    @Override
    public void cancelTask(String providerRunId) {
        restUtils.genericPost(
                address + "/api/v2/up/executor/tasks/cancel",
                secretHeaders,
                new ExecutorTaskCancelCommandRes(providerRunId),
                Object.class
        );
    }

    @Override
    public ExecutorTaskStatusRes getTaskStatus(String providerRunId) {
        return restUtils.genericGet(
                address + "/api/v2/up/executor/tasks/status?providerRunId=" + encode(providerRunId),
                secretHeaders,
                null,
                ExecutorTaskStatusRes.class
        );
    }

    @Override
    public ExecutorTaskLogsRes getTaskLogs(String providerRunId) {
        return restUtils.genericGet(
                address + "/api/v2/up/executor/tasks/logs?providerRunId=" + encode(providerRunId),
                secretHeaders,
                null,
                ExecutorTaskLogsRes.class
        );
    }

    private static String encode(String providerRunId) {
        return UriUtils.encodeQueryParam(providerRunId, StandardCharsets.UTF_8);
    }
}
