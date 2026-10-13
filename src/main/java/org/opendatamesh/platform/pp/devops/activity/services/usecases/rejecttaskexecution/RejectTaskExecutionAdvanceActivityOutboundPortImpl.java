package org.opendatamesh.platform.pp.devops.activity.services.usecases.rejecttaskexecution;

import org.opendatamesh.platform.pp.devops.activity.services.usecases.advance.AdvanceActivityCommand;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.advance.AdvanceActivityFactory;

class RejectTaskExecutionAdvanceActivityOutboundPortImpl implements RejectTaskExecutionAdvanceActivityOutboundPort {

    private final AdvanceActivityFactory advanceActivityFactory;

    RejectTaskExecutionAdvanceActivityOutboundPortImpl(AdvanceActivityFactory advanceActivityFactory) {
        this.advanceActivityFactory = advanceActivityFactory;
    }

    @Override
    public void advanceActivity(String activityUuid) {
        advanceActivityFactory.buildAdvanceActivity(new AdvanceActivityCommand(activityUuid), activity -> {
        }).execute();
    }
}
