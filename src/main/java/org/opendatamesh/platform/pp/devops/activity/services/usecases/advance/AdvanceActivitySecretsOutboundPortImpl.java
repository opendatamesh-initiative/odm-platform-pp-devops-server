package org.opendatamesh.platform.pp.devops.activity.services.usecases.advance;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.executor.ExecutorSecretsStore;

class AdvanceActivitySecretsOutboundPortImpl implements AdvanceActivitySecretsOutboundPort {

    private final ExecutorSecretsStore executorSecretsStore;

    AdvanceActivitySecretsOutboundPortImpl(ExecutorSecretsStore executorSecretsStore) {
        this.executorSecretsStore = executorSecretsStore;
    }

    @Override
    public void removeExecutorSecrets(Activity activity) {
        executorSecretsStore.removeAll(activity.getUuid());
    }
}
