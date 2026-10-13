package org.opendatamesh.platform.pp.devops.client.executor.resources;

import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ExecutorParametersRes;

import java.util.Map;

public class ExecutorTaskStartCommandRes {

    private ExecutorParametersRes executorParameters;
    private Map<String, String> pipelineParameters;

    public ExecutorTaskStartCommandRes() {
    }

    public ExecutorTaskStartCommandRes(ExecutorParametersRes executorParameters, Map<String, String> pipelineParameters) {
        this.executorParameters = executorParameters;
        this.pipelineParameters = pipelineParameters;
    }

    public ExecutorParametersRes getExecutorParameters() {
        return executorParameters;
    }

    public void setExecutorParameters(ExecutorParametersRes executorParameters) {
        this.executorParameters = executorParameters;
    }

    public Map<String, String> getPipelineParameters() {
        return pipelineParameters;
    }

    public void setPipelineParameters(Map<String, String> pipelineParameters) {
        this.pipelineParameters = pipelineParameters;
    }
}
