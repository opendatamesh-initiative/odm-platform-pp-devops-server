package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity;

import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;

import java.util.Set;

public class ActivitySearchOptions {

    private String dataProductVersionUuid;
    private String dataProductFqn;
    private String dataProductVersionTag;
    private String name;
    private Set<ExecutionStatus> statuses;

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

    public String getDataProductVersionTag() {
        return dataProductVersionTag;
    }

    public void setDataProductVersionTag(String dataProductVersionTag) {
        this.dataProductVersionTag = dataProductVersionTag;
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
}
