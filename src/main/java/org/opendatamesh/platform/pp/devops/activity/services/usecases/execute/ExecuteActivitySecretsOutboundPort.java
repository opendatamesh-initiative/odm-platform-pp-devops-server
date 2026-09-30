package org.opendatamesh.platform.pp.devops.activity.services.usecases.execute;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

interface ExecuteActivitySecretsOutboundPort {
    void storeExecutorSecrets(Activity activity);
}
