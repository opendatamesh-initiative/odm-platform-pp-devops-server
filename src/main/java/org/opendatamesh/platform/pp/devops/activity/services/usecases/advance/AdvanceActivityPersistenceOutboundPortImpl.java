package org.opendatamesh.platform.pp.devops.activity.services.usecases.advance;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;

class AdvanceActivityPersistenceOutboundPortImpl implements AdvanceActivityPersistenceOutboundPort {

    private final ActivityService activityService;

    AdvanceActivityPersistenceOutboundPortImpl(ActivityService activityService) {
        this.activityService = activityService;
    }

    @Override
    public Activity findActivity(String uuid) {
        return activityService.findOne(uuid);
    }

    @Override
    public Activity save(Activity activity) {
        return activityService.overwrite(activity.getUuid(), activity);
    }
}
