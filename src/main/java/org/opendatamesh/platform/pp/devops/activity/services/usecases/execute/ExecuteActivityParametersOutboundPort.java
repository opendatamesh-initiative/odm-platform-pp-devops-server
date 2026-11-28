package org.opendatamesh.platform.pp.devops.activity.services.usecases.execute;

import org.opendatamesh.platform.pp.devops.activity.entities.ExecutorParameters;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;

import java.util.Map;

interface ExecuteActivityParametersOutboundPort {

    /**
     * The executor parameters of the task, or {@code null} when they are missing or cannot be read.
     */
    ExecutorParameters readExecutorParameters(Task task);

    /**
     * The pipeline parameters of the task as key-value pairs, or {@code null} when the task has none.
     *
     * @throws IllegalArgumentException when they are not a JSON object of strings
     */
    Map<String, String> readPipelineParameters(Task task);
}
