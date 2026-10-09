package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskstatus;

interface RecordTaskStatusAdvanceActivityOutboundPort {
    void advanceActivity(String activityUuid);
}
