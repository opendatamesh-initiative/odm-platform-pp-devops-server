package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.utils.resources.VersionedRes;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Schema(name = "tasks")
public class TaskRes extends VersionedRes {

    @Schema(description = "The unique identifier of the task")
    private String uuid;

    @Schema(description = "UUID of the parent activity")
    private String activityUuid;

    @Schema(description = "Optional task name")
    private String name;

    @Schema(description = "Optional task description")
    private String description;

    @Schema(description = "Descriptor definition order")
    private Integer sortOrder;

    @Schema(description = "Stored execution status. Defaults to PENDING when omitted.")
    private ExecutionStatus status;

    @Schema(description = "Opaque identifier of an external pipeline run")
    private String providerRunId;

    @Schema(description = "Client-owned execution start timestamp")
    private Date startedAt;

    @Schema(description = "Client-owned execution finish timestamp")
    private Date finishedAt;

    @Schema(description = "Name of the executor, as declared in DevOps configuration")
    private String executorName;

    @Schema(description = "What the executor needs to find and start the pipeline. Not secret: stored, returned, and sent in events.")
    private ExecutorParametersRes executorParameters;

    @Schema(description = "Arguments passed to the pipeline run. Values may contain ${…} placeholders. Not secret.")
    private Map<String, String> pipelineParameters;

    @Schema(description = "Owned log records")
    private List<TaskLogRes> logs;

    @Schema(description = "Owned result records")
    private List<TaskResultRes> results;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getActivityUuid() {
        return activityUuid;
    }

    public void setActivityUuid(String activityUuid) {
        this.activityUuid = activityUuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public String getProviderRunId() {
        return providerRunId;
    }

    public void setProviderRunId(String providerRunId) {
        this.providerRunId = providerRunId;
    }

    public Date getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Date startedAt) {
        this.startedAt = startedAt;
    }

    public Date getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Date finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getExecutorName() {
        return executorName;
    }

    public void setExecutorName(String executorName) {
        this.executorName = executorName;
    }

    public ExecutorParametersRes getExecutorParameters() {
        return executorParameters;
    }

    public void setExecutorParameters(ExecutorParametersRes executorParameters) {
        this.executorParameters = executorParameters;
    }

    public Map<String, String> getPipelineParameters() {
        return pipelineParameters;
    }

    public void setPipelineParameters(Map<String, String> pipelineParameters) {
        this.pipelineParameters = pipelineParameters;
    }

    public List<TaskLogRes> getLogs() {
        return logs;
    }

    public void setLogs(List<TaskLogRes> logs) {
        this.logs = logs;
    }

    public List<TaskResultRes> getResults() {
        return results;
    }

    public void setResults(List<TaskResultRes> results) {
        this.results = results;
    }
}
