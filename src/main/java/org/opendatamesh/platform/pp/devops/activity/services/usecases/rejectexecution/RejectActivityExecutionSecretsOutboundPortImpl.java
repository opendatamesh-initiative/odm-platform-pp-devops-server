package org.opendatamesh.platform.pp.devops.activity.services.usecases.rejectexecution;

import org.opendatamesh.platform.pp.devops.executor.ExecutorSecretsStore;

class RejectActivityExecutionSecretsOutboundPortImpl implements RejectActivityExecutionSecretsOutboundPort {

    private final ExecutorSecretsStore executorSecretsStore;

    RejectActivityExecutionSecretsOutboundPortImpl(ExecutorSecretsStore executorSecretsStore) {
        this.executorSecretsStore = executorSecretsStore;
    }

    @Override
    public void removeExecutorSecrets(String activityUuid) {
        executorSecretsStore.removeAll(activityUuid);
    }
}
