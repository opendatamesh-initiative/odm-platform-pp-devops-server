package org.opendatamesh.platform.pp.devops.activity.services.usecases.requesttaskexecution;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivitySearchOptions;
import org.opendatamesh.platform.pp.devops.utils.services.EntityInitAndDetachService;
import org.springframework.data.domain.Pageable;

import java.util.EnumSet;
import java.util.List;

class RequestTaskExecutionPersistenceOutboundPortImpl implements RequestTaskExecutionPersistenceOutboundPort {

    private final ActivityService activityService;
    private final EntityInitAndDetachService entityInitAndDetachService;

    RequestTaskExecutionPersistenceOutboundPortImpl(ActivityService activityService,
                                                    EntityInitAndDetachService entityInitAndDetachService) {
        this.activityService = activityService;
        this.entityInitAndDetachService = entityInitAndDetachService;
    }

    @Override
    public List<Activity> findOpenActivities(String dataProductFqn, String dataProductVersionTag, String activityName) {
        ActivitySearchOptions options = new ActivitySearchOptions();
        options.setDataProductFqn(dataProductFqn);
        options.setDataProductVersionTag(dataProductVersionTag);
        options.setName(activityName);
        options.setStatuses(EnumSet.of(ExecutionStatus.PENDING, ExecutionStatus.RUNNING));
        return activityService.findAllFiltered(Pageable.unpaged(), options).getContent();
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
}
