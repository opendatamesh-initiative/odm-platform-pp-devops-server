package org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.utils.services.EntityInitAndDetachService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

class CancelActivityPersistenceOutboundPortImpl implements CancelActivityPersistenceOutboundPort {

    private final ActivityService activityService;
    private final EntityInitAndDetachService entityInitAndDetachService;
    private final TransactionTemplate committedReads;

    CancelActivityPersistenceOutboundPortImpl(ActivityService activityService,
                                              PlatformTransactionManager transactionManager,
                                              EntityInitAndDetachService entityInitAndDetachService) {
        this.activityService = activityService;
        this.entityInitAndDetachService = entityInitAndDetachService;
        this.committedReads = new TransactionTemplate(transactionManager);
        this.committedReads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.committedReads.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.committedReads.setReadOnly(true);
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

    /**
     * The cancel wait calls this between sleeps, outside the serializable write.
     * Each pass must commit before the next sleep. A serializable read here would share
     * Execute Task's snapshot and could abort the outcome write, which is not retried.
     * findDetached does not. The graph is detached so status and provider run id can be
     * read after this transaction ends. {@link #findActivity} only refreshes inside the
     * caller's transaction and leaves the activity managed.
     */
    @Override
    public Activity findDetached(String uuid) {
        return committedReads.execute(status -> {
            Activity activity = findActivity(uuid);
            entityInitAndDetachService.initializeEntityAndDetach(activity);
            return activity;
        });
    }
}
