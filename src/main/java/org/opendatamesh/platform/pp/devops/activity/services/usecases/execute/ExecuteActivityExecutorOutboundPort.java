package org.opendatamesh.platform.pp.devops.activity.services.usecases.execute;

import org.opendatamesh.platform.pp.devops.executor.ExecutorInfo;

import java.util.Optional;

interface ExecuteActivityExecutorOutboundPort {
    Optional<ExecutorInfo> findExecutor(String executorName);
}
