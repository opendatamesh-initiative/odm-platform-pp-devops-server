package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.devops.activity.entities.GitRefType;

@Schema(name = "GitRef")
public class GitRefRes {

    @Schema(description = "Bare git ref name, such as v1.2.0")
    private String name;

    @Schema(description = "Whether the ref is a tag or a branch")
    private GitRefType type;

    public GitRefRes() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public GitRefType getType() {
        return type;
    }

    public void setType(GitRefType type) {
        this.type = type;
    }
}
