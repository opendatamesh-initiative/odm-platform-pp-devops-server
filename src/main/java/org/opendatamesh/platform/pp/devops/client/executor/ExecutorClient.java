package org.opendatamesh.platform.pp.devops.client.executor;

import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskLogsRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskRunCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartResultRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStatusRes;

public interface ExecutorClient {

    ExecutorTaskStartResultRes startTask(ExecutorTaskStartCommandRes command);

    void cancelTask(ExecutorTaskRunCommandRes command);

    ExecutorTaskStatusRes getTaskStatus(ExecutorTaskRunCommandRes command);

    ExecutorTaskLogsRes getTaskLogs(ExecutorTaskRunCommandRes command);
}
