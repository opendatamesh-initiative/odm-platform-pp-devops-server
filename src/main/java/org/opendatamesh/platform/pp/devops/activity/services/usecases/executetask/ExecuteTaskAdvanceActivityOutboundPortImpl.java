package org.opendatamesh.platform.pp.devops.activity.services.usecases.executetask;

import org.opendatamesh.platform.pp.devops.activity.services.usecases.advance.AdvanceActivityCommand;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.advance.AdvanceActivityFactory;

class ExecuteTaskAdvanceActivityOutboundPortImpl implements ExecuteTaskAdvanceActivityOutboundPort {

    private final AdvanceActivityFactory advanceActivityFactory;

    ExecuteTaskAdvanceActivityOutboundPortImpl(AdvanceActivityFactory advanceActivityFactory) {
        this.advanceActivityFactory = advanceActivityFactory;
    }

    @Override
    public void advanceActivity(String activityUuid) {
        advanceActivityFactory.buildAdvanceActivity(new AdvanceActivityCommand(activityUuid), activity -> {
        }).execute();
    }
}
