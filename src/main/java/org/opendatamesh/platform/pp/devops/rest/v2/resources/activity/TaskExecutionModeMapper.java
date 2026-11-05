package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity;

import org.mapstruct.Named;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
import org.opendatamesh.platform.pp.devops.executor.ExecutorInfo;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolves a task's execution mode from the current declaration of its executor, when the task is mapped to a
 * resource. Unlike the use-case lookups, it never throws: an executor that is no longer declared gives {@code null}.
 */
@Component
public class TaskExecutionModeMapper {

    private final ExecutorServicesProperties executorServicesProperties;

    public TaskExecutionModeMapper(ExecutorServicesProperties executorServicesProperties) {
        this.executorServicesProperties = executorServicesProperties;
    }

    @Named("executionMode")
    public ExecutionMode executionModeOf(String executorName) {
        if (!StringUtils.hasText(executorName)) {
            return null;
        }
        return executorServicesProperties.findExecutor(executorName)
                .map(ExecutorInfo::executionMode)
                .orElse(null);
    }
}
