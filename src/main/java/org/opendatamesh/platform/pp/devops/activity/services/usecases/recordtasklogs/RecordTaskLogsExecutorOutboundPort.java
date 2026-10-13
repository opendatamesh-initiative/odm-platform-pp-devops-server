package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtasklogs;

import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;

interface RecordTaskLogsExecutorOutboundPort {
    ExecutionMode findExecutionMode(String executorName);
}
