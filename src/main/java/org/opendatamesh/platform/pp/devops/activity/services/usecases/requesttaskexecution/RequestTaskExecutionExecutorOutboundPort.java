package org.opendatamesh.platform.pp.devops.activity.services.usecases.requesttaskexecution;

import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;

interface RequestTaskExecutionExecutorOutboundPort {
    ExecutionMode findExecutionMode(String executorName);
}
