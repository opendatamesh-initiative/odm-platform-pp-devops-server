package org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.utils.services.EntityInitAndDetachService;

class CancelActivityPersistenceOutboundPortImpl implements CancelActivityPersistenceOutboundPort {

    private final ActivityService activityService;
    private final EntityInitAndDetachService entityInitAndDetachService;

    CancelActivityPersistenceOutboundPortImpl(ActivityService activityService,
                                              EntityInitAndDetachService entityInitAndDetachService) {
        this.activityService = activityService;
        this.entityInitAndDetachService = entityInitAndDetachService;
    }

    @Override
    public Activity findForUpdate(String uuid) {
        Activity activity = activityService.findOne(uuid);
        entityInitAndDetachService.refresh(activity);
        return activity;
    }

    @Override
    public Activity save(Activity activity) {
        return activityService.overwrite(activity.getUuid(), activity);
    }

    /**
     * Loads the whole graph and detaches it, so status and provider run id can be read after the
     * transaction ends. Cancel calls it through a read-committed transaction between sleeps: a serializable
     * read there would share Execute Task's snapshot and could abort its outcome write.
     */
    @Override
    public Activity findDetached(String uuid) {
        Activity activity = findForUpdate(uuid);
        entityInitAndDetachService.initializeEntityAndDetach(activity);
        return activity;
    }
}
