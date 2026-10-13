package org.opendatamesh.platform.pp.devops.client.executor.resources;

public class ExecutorTaskStatusRes {

    private String providerRunId;
    private String status;

    public ExecutorTaskStatusRes() {
    }

    public String getProviderRunId() {
        return providerRunId;
    }

    public void setProviderRunId(String providerRunId) {
        this.providerRunId = providerRunId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
