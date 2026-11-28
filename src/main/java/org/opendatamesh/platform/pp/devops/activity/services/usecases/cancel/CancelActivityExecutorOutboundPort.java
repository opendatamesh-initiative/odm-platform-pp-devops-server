package org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel;

import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;

interface CancelActivityExecutorOutboundPort {
    /** The execution mode of a declared executor, or {@code null} when the executor is no longer declared. */
    ExecutionMode findExecutionMode(String executorName);

    /** Whether this DevOps instance is following the run of the task and will record its outcome. */
    boolean isRunFollowed(String taskUuid);

    CancelRunResult cancelRun(Task task);
}
