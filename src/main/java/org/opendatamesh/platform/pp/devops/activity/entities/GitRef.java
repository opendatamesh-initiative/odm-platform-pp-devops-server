package org.opendatamesh.platform.pp.devops.activity.entities;

/**
 * Value object inside the executor-parameters JSON of a task.
 * Hibernate does not map this type. It has no table.
 */
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
