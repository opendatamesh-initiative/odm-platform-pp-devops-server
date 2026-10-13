package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskstatus;

import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;

interface RecordTaskStatusExecutorOutboundPort {
    ExecutionMode findExecutionMode(String executorName);
}
