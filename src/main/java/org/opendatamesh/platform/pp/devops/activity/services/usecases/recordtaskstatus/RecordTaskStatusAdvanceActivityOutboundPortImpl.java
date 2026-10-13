package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskstatus;

import org.opendatamesh.platform.pp.devops.activity.services.usecases.advance.AdvanceActivityCommand;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.advance.AdvanceActivityFactory;

class RecordTaskStatusAdvanceActivityOutboundPortImpl implements RecordTaskStatusAdvanceActivityOutboundPort {

    private final AdvanceActivityFactory advanceActivityFactory;

    RecordTaskStatusAdvanceActivityOutboundPortImpl(AdvanceActivityFactory advanceActivityFactory) {
        this.advanceActivityFactory = advanceActivityFactory;
    }

    @Override
    public void advanceActivity(String activityUuid) {
        advanceActivityFactory.buildAdvanceActivity(new AdvanceActivityCommand(activityUuid), activity -> {
        }).execute();
    }
}
