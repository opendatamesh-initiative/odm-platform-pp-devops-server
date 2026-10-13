package org.opendatamesh.platform.pp.devops.activity.services.usecases.executetask;

import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskLog;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
import org.opendatamesh.platform.pp.devops.executor.ExecutorRunStatus;

import java.util.Map;
import java.util.Optional;

interface ExecuteTaskExecutorOutboundPort {
    ExecutionMode findExecutionMode(String executorName);

    String startRun(Task task, Map<String, String> resolvedPipelineParameters);

    ExecutorRunStatus readRunStatus(Task task);

    void waitBeforeNextStatusRead();

    int maxStatusReads();

    Optional<TaskLog> readRunLog(Task task);
}
