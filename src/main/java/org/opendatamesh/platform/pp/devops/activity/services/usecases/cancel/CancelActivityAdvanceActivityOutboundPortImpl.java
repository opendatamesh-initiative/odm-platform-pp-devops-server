package org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel;

import org.opendatamesh.platform.pp.devops.activity.services.usecases.advance.AdvanceActivityCommand;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.advance.AdvanceActivityFactory;

class CancelActivityAdvanceActivityOutboundPortImpl implements CancelActivityAdvanceActivityOutboundPort {

    private final AdvanceActivityFactory advanceActivityFactory;

    CancelActivityAdvanceActivityOutboundPortImpl(AdvanceActivityFactory advanceActivityFactory) {
        this.advanceActivityFactory = advanceActivityFactory;
    }

    @Override
    public void advanceActivity(String activityUuid) {
        advanceActivityFactory.buildAdvanceActivity(new AdvanceActivityCommand(activityUuid), activity -> {
        }).execute();
    }
}
