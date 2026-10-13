package org.opendatamesh.platform.pp.devops.activity.services.usecases.approveexecution;

import org.opendatamesh.platform.pp.devops.activity.services.usecases.advance.AdvanceActivityCommand;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.advance.AdvanceActivityFactory;

class ApproveActivityExecutionAdvanceActivityOutboundPortImpl implements ApproveActivityExecutionAdvanceActivityOutboundPort {

    private final AdvanceActivityFactory advanceActivityFactory;

    ApproveActivityExecutionAdvanceActivityOutboundPortImpl(AdvanceActivityFactory advanceActivityFactory) {
        this.advanceActivityFactory = advanceActivityFactory;
    }

    @Override
    public void advanceActivity(String activityUuid) {
        advanceActivityFactory.buildAdvanceActivity(new AdvanceActivityCommand(activityUuid), activity -> {
        }).execute();
    }
}
