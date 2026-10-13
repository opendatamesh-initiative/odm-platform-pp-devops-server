package org.opendatamesh.platform.pp.devops.activity.services.usecases.advance;

import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;

interface AdvanceActivityExecutorOutboundPort {
    ExecutionMode findExecutionMode(String executorName);
}
