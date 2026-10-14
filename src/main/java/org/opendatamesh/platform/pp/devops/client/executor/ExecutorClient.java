package org.opendatamesh.platform.pp.devops.client.executor;

import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskLogsRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskCancelCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskLogsCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStatusCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartResultRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStatusRes;

public interface ExecutorClient {

    ExecutorTaskStartResultRes startTask(ExecutorTaskStartCommandRes command);

    void cancelTask(ExecutorTaskCancelCommandRes command);

    ExecutorTaskStatusRes getTaskStatus(ExecutorTaskStatusCommandRes command);

    ExecutorTaskLogsRes getTaskLogs(ExecutorTaskLogsCommandRes command);
}
