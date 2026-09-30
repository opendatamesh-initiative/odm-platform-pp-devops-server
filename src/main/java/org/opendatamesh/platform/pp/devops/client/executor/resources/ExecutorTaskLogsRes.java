package org.opendatamesh.platform.pp.devops.client.executor.resources;

import java.util.Date;

public class ExecutorTaskLogsRes {

    private String content;
    private Date generatedAt;

    public ExecutorTaskLogsRes() {
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
