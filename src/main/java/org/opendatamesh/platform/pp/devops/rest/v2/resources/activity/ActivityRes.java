package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.utils.resources.VersionedRes;

import java.util.Date;
import java.util.List;

@Schema(name = "activities")
public class ActivityRes extends VersionedRes {

    @Schema(description = "The unique identifier of the activity")
    private String uuid;

    @Schema(description = "Registry data product version UUID")
    private String dataProductVersionUuid;

    @Schema(description = "Data product fully qualified name")
    private String dataProductFqn;

    @Schema(description = "Data product version tag")
    private String dataProductVersionTag;

    @Schema(description = "Activity name")
    private String name;

    @Schema(description = "Descriptor definition order")
    private Integer sortOrder;

    @Schema(description = "Stored execution status. Defaults to PENDING when omitted.")
    private ExecutionStatus status;

    @Schema(description = "Client-owned execution start timestamp")
    private Date startedAt;

    @Schema(description = "Client-owned execution finish timestamp")
    private Date finishedAt;

    @Schema(description = "Owned tasks. Present on get-by-id; omitted or empty on search.")
    private List<TaskRes> tasks;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

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

    public List<TaskRes> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskRes> tasks) {
        this.tasks = tasks;
    }
}
