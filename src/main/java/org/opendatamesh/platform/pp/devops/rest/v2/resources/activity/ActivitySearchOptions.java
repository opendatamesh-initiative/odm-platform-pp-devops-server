package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity;

import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;

import java.util.Set;

public class ActivitySearchOptions {

    private String dataProductVersionUuid;
    private String dataProductFqn;
    private String dataProductVersionNumber;
    private String name;
    private Set<ExecutionStatus> statuses;
    private String taskProviderRunId;

    public String getDataProductVersionUuid() {
        return dataProductVersionUuid;
    }

    public void setDataProductVersionUuid(String dataProductVersionUuid) {
        this.dataProductVersionUuid = dataProductVersionUuid;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<ExecutionStatus> getStatuses() {
        return statuses;
    }

    public void setStatuses(Set<ExecutionStatus> statuses) {
        this.statuses = statuses;
    }

    public String getTaskProviderRunId() {
        return taskProviderRunId;
    }

    public void setTaskProviderRunId(String taskProviderRunId) {
        this.taskProviderRunId = taskProviderRunId;
    }
}
