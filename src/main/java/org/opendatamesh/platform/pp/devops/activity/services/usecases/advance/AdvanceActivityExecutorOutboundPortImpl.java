package org.opendatamesh.platform.pp.devops.activity.services.usecases.advance;

import org.opendatamesh.platform.pp.devops.exceptions.InternalException;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;

class AdvanceActivityExecutorOutboundPortImpl implements AdvanceActivityExecutorOutboundPort {

    private final ExecutorServicesProperties executorServicesProperties;

    AdvanceActivityExecutorOutboundPortImpl(ExecutorServicesProperties executorServicesProperties) {
        this.executorServicesProperties = executorServicesProperties;
    }

    @Override
    public ExecutionMode findExecutionMode(String executorName) {
        return executorServicesProperties.findExecutor(executorName)
                .map(executor -> executor.executionMode())
                .orElseThrow(() -> new InternalException("Executor " + executorName + " is not declared"));
    }
}
