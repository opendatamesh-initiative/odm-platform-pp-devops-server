package org.opendatamesh.platform.pp.devops.client.executor;

public interface ExecutorClientFactory {

    ExecutorClient getExecutorClient(String executorName, String activityUuid);
}
