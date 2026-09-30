package org.opendatamesh.platform.pp.devops.activity.services.usecases.execute;

import org.opendatamesh.platform.pp.devops.executor.ExecutorInfo;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;

import java.util.Optional;

class ExecuteActivityExecutorOutboundPortImpl implements ExecuteActivityExecutorOutboundPort {

    private final ExecutorServicesProperties executorServicesProperties;

    ExecuteActivityExecutorOutboundPortImpl(ExecutorServicesProperties executorServicesProperties) {
        this.executorServicesProperties = executorServicesProperties;
    }

    @Override
    public Optional<ExecutorInfo> findExecutor(String executorName) {
        return executorServicesProperties.findExecutor(executorName);
    }
}
