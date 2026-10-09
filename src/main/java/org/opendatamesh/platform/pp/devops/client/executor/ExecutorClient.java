package org.opendatamesh.platform.pp.devops.client.executor;

import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskLogsRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartResultRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStatusRes;

public interface ExecutorClient {

    ExecutorTaskStartResultRes startTask(ExecutorTaskStartCommandRes command);

    void cancelTask(String providerRunId);

    ExecutorTaskStatusRes getTaskStatus(String providerRunId);

    ExecutorTaskLogsRes getTaskLogs(String providerRunId);
}
