package org.opendatamesh.platform.pp.devops.client.executor.resources;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ExecutorParametersRes;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExecutorTaskCancelCommandRes {

    private ExecutorParametersRes executorParameters;
    private String providerRunId;

    public ExecutorTaskCancelCommandRes() {
    }

    public ExecutorTaskCancelCommandRes(ExecutorParametersRes executorParameters, String providerRunId) {
        this.executorParameters = executorParameters;
        this.providerRunId = providerRunId;
    }

    public ExecutorParametersRes getExecutorParameters() {
        return executorParameters;
    }

    public void setExecutorParameters(ExecutorParametersRes executorParameters) {
        this.executorParameters = executorParameters;
    }

    public String getProviderRunId() {
        return providerRunId;
    }

    public void setProviderRunId(String providerRunId) {
        this.providerRunId = providerRunId;
    }
}
