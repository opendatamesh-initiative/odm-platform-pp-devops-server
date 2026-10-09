package org.opendatamesh.platform.pp.devops.activity.entities;

public class GitRef {

    private String name;
    private GitRefType type;

    public GitRef() {
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
