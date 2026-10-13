package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.task;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskResultRes;

import java.util.List;

@Schema(name = "ActivityTaskResultsCommand", description = "Results to append to one running task")
public class ActivityTaskResultsCommandRes {

    @Schema(description = "UUID of the activity. When set, the data product, version tag, and activity name are ignored")
    private String activityUuid;

    @Schema(description = "Fully qualified name of the data product, used when the activity UUID is absent")
    private String dataProductFqn;

    @Schema(description = "Version tag of the data product, used when the activity UUID is absent")
    private String dataProductVersionTag;

    @Schema(description = "Name of the open activity, used when the activity UUID is absent")
    private String activityName;

    @Schema(description = "Name of the task on that activity")
    private String taskName;

    @Schema(description = "Result rows to append. Each row carries content and an optional source time")
    private List<TaskResultRes> results;

    public ActivityTaskResultsCommandRes() {
    }

    public String getActivityUuid() {
        return activityUuid;
    }

    public void setActivityUuid(String activityUuid) {
        this.activityUuid = activityUuid;
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

    public List<TaskResultRes> getResults() {
        return results;
    }

    public void setResults(List<TaskResultRes> results) {
        this.results = results;
    }
}
