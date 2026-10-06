package org.opendatamesh.platform.pp.devops.activity.services.usecases.rejecttaskexecution;

interface RejectTaskExecutionAdvanceActivityOutboundPort {
    void advanceActivity(String activityUuid);
}
