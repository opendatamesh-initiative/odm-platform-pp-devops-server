package org.opendatamesh.platform.pp.devops.executor;

import java.util.Map;

public interface ExecutorSecretsStore {

    void store(String executorName, String activityUuid, Map<String, String> secretHeaders);

    Map<String, String> find(String executorName, String activityUuid);

    void removeAll(String activityUuid);
}
