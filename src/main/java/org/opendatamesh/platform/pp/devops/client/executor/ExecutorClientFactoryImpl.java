package org.opendatamesh.platform.pp.devops.client.executor;

import org.opendatamesh.platform.pp.devops.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.devops.executor.ExecutorSecretsStore;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;
import org.opendatamesh.platform.pp.devops.utils.client.RestUtils;
import org.opendatamesh.platform.pp.devops.utils.client.http.HttpHeader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ExecutorClientFactoryImpl implements ExecutorClientFactory {

    private final ExecutorServicesProperties executorServicesProperties;
    private final ExecutorSecretsStore executorSecretsStore;
    private final RestUtils restUtils;

    ExecutorClientFactoryImpl(ExecutorServicesProperties executorServicesProperties,
                              ExecutorSecretsStore executorSecretsStore,
                              RestUtils restUtils) {
        this.executorServicesProperties = executorServicesProperties;
        this.executorSecretsStore = executorSecretsStore;
        this.restUtils = restUtils;
    }

    @Override
    public ExecutorClient getExecutorClient(String executorName, String activityUuid) {
        String address = executorServicesProperties.findAddress(executorName)
                .orElseThrow(() -> new NotFoundException("Executor " + executorName + " is not declared"));
        Map<String, String> secrets = executorSecretsStore.find(executorName, activityUuid);
        List<HttpHeader> headers = new ArrayList<>();
        for (Map.Entry<String, String> secret : secrets.entrySet()) {
            headers.add(new HttpHeader(secret.getKey(), secret.getValue()));
        }
        return new ExecutorClientImpl(address, headers, restUtils);
    }
}
