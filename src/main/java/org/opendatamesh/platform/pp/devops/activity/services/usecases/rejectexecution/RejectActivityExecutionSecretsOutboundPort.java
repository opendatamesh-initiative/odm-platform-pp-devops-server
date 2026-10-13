package org.opendatamesh.platform.pp.devops.activity.services.usecases.rejectexecution;

interface RejectActivityExecutionSecretsOutboundPort {
    void removeExecutorSecrets(String activityUuid);
}
