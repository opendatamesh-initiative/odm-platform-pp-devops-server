package org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel;

import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;

interface CancelActivityExecutorOutboundPort {
    ExecutionMode findExecutionMode(String executorName);

    CancelRunResult cancelRun(Task task);
}
