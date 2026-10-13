package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.task;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;

@Schema(name = "ActivityTaskStatusCommand", description = "Terminal status of one running instrumented task")
public class ActivityTaskStatusCommandRes {

    @Schema(description = "Fully qualified name of the data product")
    private String dataProductFqn;

    @Schema(description = "Version tag of the data product")
    private String dataProductVersionTag;

    @Schema(description = "Name of the open activity")
    private String activityName;

    @Schema(description = "Name of the task on that activity")
    private String taskName;

    @Schema(description = "Terminal status: SUCCEEDED, FAILED, or CANCELED")
    private ExecutionStatus status;

    public ActivityTaskStatusCommandRes() {
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

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }
}
