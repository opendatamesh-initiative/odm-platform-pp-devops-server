package org.opendatamesh.platform.pp.devops.activity.services.usecases.advance;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

interface AdvanceActivitySecretsOutboundPort {
    void removeExecutorSecrets(Activity activity);
}
