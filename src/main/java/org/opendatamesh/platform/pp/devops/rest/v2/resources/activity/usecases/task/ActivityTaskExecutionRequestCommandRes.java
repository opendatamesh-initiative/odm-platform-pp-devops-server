package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.task;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ActivityTaskExecutionRequestCommand", description = "Request to run the next pending instrumented task")
public class ActivityTaskExecutionRequestCommandRes {

    @Schema(description = "Fully qualified name of the data product")
    private String dataProductFqn;

    @Schema(description = "Version tag of the data product")
    private String dataProductVersionTag;

    @Schema(description = "Name of the open activity")
    private String activityName;

    @Schema(description = "Name of the next pending task on that activity")
    private String taskName;

    @Schema(description = "Provider run id to store while the task is pending")
    private String providerRunId;

    public ActivityTaskExecutionRequestCommandRes() {
    }

    public String getDataProductFqn() {
        return dataProductFqn;
    }

    public void setDataProductFqn(String dataProductFqn) {
        this.dataProductFqn = dataProductFqn;
    }

    public String getDataProductVersionTag() {
        return dataProductVersionTag;
    }

    public void setDataProductVersionTag(String dataProductVersionTag) {
        this.dataProductVersionTag = dataProductVersionTag;
    }

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getProviderRunId() {
        return providerRunId;
    }

    public void setProviderRunId(String providerRunId) {
        this.providerRunId = providerRunId;
    }
}
