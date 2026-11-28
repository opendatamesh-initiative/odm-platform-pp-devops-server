package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskstatus;

import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;

interface RecordTaskStatusExecutorOutboundPort {
    /** The execution mode of a declared executor, or {@code null} when the executor is no longer declared. */
    ExecutionMode findExecutionMode(String executorName);
}
