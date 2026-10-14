package org.opendatamesh.platform.pp.devops.client.executor;

import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskLogsRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskCancelCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskLogsCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStatusCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartResultRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStatusRes;
import org.opendatamesh.platform.pp.devops.utils.client.RestUtils;
import org.opendatamesh.platform.pp.devops.utils.client.http.HttpHeader;

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
    public void cancelTask(ExecutorTaskCancelCommandRes command) {
        restUtils.genericPost(
                address + "/api/v2/up/executor/tasks/cancel",
                secretHeaders,
                command,
                Object.class
        );
    }

    @Override
    public ExecutorTaskStatusRes getTaskStatus(ExecutorTaskStatusCommandRes command) {
        return restUtils.genericPost(
                address + "/api/v2/up/executor/tasks/status",
                secretHeaders,
                command,
                ExecutorTaskStatusRes.class
        );
    }

    @Override
    public ExecutorTaskLogsRes getTaskLogs(ExecutorTaskLogsCommandRes command) {
        return restUtils.genericPost(
                address + "/api/v2/up/executor/tasks/logs",
                secretHeaders,
                command,
                ExecutorTaskLogsRes.class
        );
    }
}
