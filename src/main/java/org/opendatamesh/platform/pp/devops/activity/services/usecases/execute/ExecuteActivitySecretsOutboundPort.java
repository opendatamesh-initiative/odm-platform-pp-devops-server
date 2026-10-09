package org.opendatamesh.platform.pp.devops.activity.services.usecases.execute;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

import java.util.Set;

interface ExecuteActivitySecretsOutboundPort {
    void storeExecutorSecrets(Activity activity, Set<String> executorNames);
}
