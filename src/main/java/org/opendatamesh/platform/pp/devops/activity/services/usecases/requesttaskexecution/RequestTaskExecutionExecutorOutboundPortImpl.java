package org.opendatamesh.platform.pp.devops.activity.services.usecases.requesttaskexecution;

import org.opendatamesh.platform.pp.devops.exceptions.InternalException;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;

class RequestTaskExecutionExecutorOutboundPortImpl implements RequestTaskExecutionExecutorOutboundPort {

    private final ExecutorServicesProperties executorServicesProperties;

    RequestTaskExecutionExecutorOutboundPortImpl(ExecutorServicesProperties executorServicesProperties) {
        this.executorServicesProperties = executorServicesProperties;
    }

    @Override
    public ExecutionMode findExecutionMode(String executorName) {
        return executorServicesProperties.findExecutor(executorName)
                .map(executor -> executor.executionMode())
                .orElseThrow(() -> new InternalException("Executor " + executorName + " is not declared"));
    }
}
