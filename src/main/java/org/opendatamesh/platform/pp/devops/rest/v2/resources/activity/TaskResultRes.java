package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.devops.utils.resources.VersionedRes;

import java.util.Date;

@Schema(name = "task_results")
public class TaskResultRes extends VersionedRes {

    @Schema(description = "The unique identifier of the result record")
    private String uuid;

    @Schema(description = "Result content")
    private String content;

    @Schema(description = "Source timestamp at the producer")
    private Date generatedAt;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Date getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Date generatedAt) {
        this.generatedAt = generatedAt;
    }
}
