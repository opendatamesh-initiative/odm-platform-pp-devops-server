package org.opendatamesh.platform.pp.devops.client.executor.resources;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExecutorTaskCancelCommandRes {

    private String providerRunId;

    public ExecutorTaskCancelCommandRes() {
    }

    public ExecutorTaskCancelCommandRes(String providerRunId) {
        this.providerRunId = providerRunId;
    }

    public String getProviderRunId() {
        return providerRunId;
    }

    public void setProviderRunId(String providerRunId) {
        this.providerRunId = providerRunId;
    }
}
