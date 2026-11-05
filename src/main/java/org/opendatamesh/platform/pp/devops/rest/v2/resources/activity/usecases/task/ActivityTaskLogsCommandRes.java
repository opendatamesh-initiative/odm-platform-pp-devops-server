package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.task;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskLogRes;

import java.util.List;

@Schema(name = "ActivityTaskLogsCommand", description = "Logs to append to one running instrumented task")
public class ActivityTaskLogsCommandRes {

    @Schema(description = "Provider run id of the RUNNING task to address. Use it instead of the other address fields. With it, taskName is optional and narrows the match.")
    private String providerRunId;

    @Schema(description = "Alternative address: fully qualified name of the data product, sent together with the version number and activity name")
    private String dataProductFqn;

    @Schema(description = "Alternative address: version number of the data product, sent together with the FQN and activity name")
    private String dataProductVersionNumber;

    @Schema(description = "Alternative address: name of the open activity, sent together with the FQN and version number")
    private String activityName;

    @Schema(description = "Name of the task on that activity. Required with the data product address, optional with the provider run id, where it narrows the match")
    private String taskName;

    @Schema(description = "Log rows to append. Each row carries content and an optional source time")
    private List<TaskLogRes> logs;

    public ActivityTaskLogsCommandRes() {
    }

    public String getProviderRunId() {
        return providerRunId;
    }

    public void setProviderRunId(String providerRunId) {
        this.providerRunId = providerRunId;
    }

    public String getDataProductFqn() {
        return dataProductFqn;
    }

    public void setDataProductFqn(String dataProductFqn) {
        this.dataProductFqn = dataProductFqn;
    }

    public String getDataProductVersionNumber() {
        return dataProductVersionNumber;
    }

    public void setDataProductVersionNumber(String dataProductVersionNumber) {
        this.dataProductVersionNumber = dataProductVersionNumber;
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

    public List<TaskLogRes> getLogs() {
        return logs;
    }

    public void setLogs(List<TaskLogRes> logs) {
        this.logs = logs;
    }
}
