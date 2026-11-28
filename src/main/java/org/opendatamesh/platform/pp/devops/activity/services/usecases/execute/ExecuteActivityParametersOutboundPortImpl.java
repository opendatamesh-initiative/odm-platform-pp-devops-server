package org.opendatamesh.platform.pp.devops.activity.services.usecases.execute;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutorParameters;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

class ExecuteActivityParametersOutboundPortImpl implements ExecuteActivityParametersOutboundPort {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public ExecutorParameters readExecutorParameters(Task task) {
        if (task.getExecutorParameters() == null || task.getExecutorParameters().isNull()
                || task.getExecutorParameters().isMissingNode()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.treeToValue(task.getExecutorParameters(), ExecutorParameters.class);
        } catch (Exception exception) {
            return null;
        }
    }

    @Override
    public Map<String, String> readPipelineParameters(Task task) {
        if (!StringUtils.hasText(task.getPipelineParameters())) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(task.getPipelineParameters(), new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception exception) {
            throw new IllegalArgumentException("Pipeline parameters are not valid JSON", exception);
        }
    }
}
