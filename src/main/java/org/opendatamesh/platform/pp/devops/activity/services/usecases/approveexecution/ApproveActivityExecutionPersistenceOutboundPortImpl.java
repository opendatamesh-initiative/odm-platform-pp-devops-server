package org.opendatamesh.platform.pp.devops.activity.services.usecases.approveexecution;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.utils.services.EntityInitAndDetachService;

class ApproveActivityExecutionPersistenceOutboundPortImpl implements ApproveActivityExecutionPersistenceOutboundPort {

    private final ActivityService activityService;
    private final EntityInitAndDetachService entityInitAndDetachService;

    ApproveActivityExecutionPersistenceOutboundPortImpl(ActivityService activityService,
                                                        EntityInitAndDetachService entityInitAndDetachService) {
        this.activityService = activityService;
        this.entityInitAndDetachService = entityInitAndDetachService;
    }

    @Override
    public Activity findActivity(String uuid) {
        Activity activity = activityService.findOne(uuid);
        entityInitAndDetachService.refresh(activity);
        return activity;
    }

    @Override
    public Activity save(Activity activity) {
        return activityService.overwrite(activity.getUuid(), activity);
    }
}
