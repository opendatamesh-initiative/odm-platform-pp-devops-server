package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.opendatamesh.platform.pp.devops.activity.entities.DataProductRepo;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutorParameters;
import org.opendatamesh.platform.pp.devops.activity.entities.GitRef;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskLog;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskResult;

import java.util.LinkedHashMap;
import java.util.Map;

@Mapper(componentModel = "spring")
public interface TaskMapper {

    @Mapping(target = "activity", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "executorParameters", source = "executorParameters", qualifiedByName = "executorParametersToJsonNode")
    @Mapping(target = "pipelineParameters", source = "pipelineParameters", qualifiedByName = "pipelineParametersToString")
    Task toEntity(TaskRes res);

    @Mapping(target = "activityUuid", source = "activity.uuid")
    @Mapping(target = "executorParameters", source = "executorParameters", qualifiedByName = "jsonNodeToExecutorParameters")
    @Mapping(target = "pipelineParameters", source = "pipelineParameters", qualifiedByName = "stringToPipelineParameters")
    TaskRes toRes(Task entity);

    @Named("withoutLogsAndResults")
    @Mapping(target = "activityUuid", source = "activity.uuid")
    @Mapping(target = "logs", ignore = true)
    @Mapping(target = "results", ignore = true)
    @Mapping(target = "executorParameters", source = "executorParameters", qualifiedByName = "jsonNodeToExecutorParameters")
    @Mapping(target = "pipelineParameters", source = "pipelineParameters", qualifiedByName = "stringToPipelineParameters")
    TaskRes toResWithoutLogsAndResults(Task entity);

    ExecutorParameters toEntity(ExecutorParametersRes res);

    ExecutorParametersRes toRes(ExecutorParameters entity);

    DataProductRepo toEntity(DataProductRepoRes res);

    DataProductRepoRes toRes(DataProductRepo entity);

    GitRef toEntity(GitRefRes res);

    GitRefRes toRes(GitRef entity);

    @Mapping(target = "task", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    TaskLog toEntity(TaskLogRes res);

    TaskLogRes toRes(TaskLog entity);

    @Mapping(target = "task", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    TaskResult toEntity(TaskResultRes res);

    TaskResultRes toRes(TaskResult entity);

    @Named("executorParametersToJsonNode")
    default JsonNode executorParametersToJsonNode(ExecutorParametersRes value) {
        if (value == null) {
            return null;
        }
        return new ObjectMapper().valueToTree(value);
    }

    @Named("jsonNodeToExecutorParameters")
    default ExecutorParametersRes jsonNodeToExecutorParameters(JsonNode json) {
        if (json == null || json.isNull() || json.isMissingNode()) {
            return null;
        }
        try {
            return new ObjectMapper().treeToValue(json, ExecutorParametersRes.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to read executor parameters");
        }
    }

    @Named("pipelineParametersToString")
    default String pipelineParametersToString(Map<String, String> value) {
        if (value == null) {
            return null;
        }
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to write pipeline parameters");
        }
    }

    @Named("stringToPipelineParameters")
    default Map<String, String> stringToPipelineParameters(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return new ObjectMapper().readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to read pipeline parameters");
        }
    }
}
