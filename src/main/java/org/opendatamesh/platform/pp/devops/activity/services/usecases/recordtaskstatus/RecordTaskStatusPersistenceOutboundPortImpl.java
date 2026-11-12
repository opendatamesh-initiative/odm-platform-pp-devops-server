package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskstatus;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivitySearchOptions;
import org.opendatamesh.platform.pp.devops.utils.services.EntityInitAndDetachService;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

class RecordTaskStatusPersistenceOutboundPortImpl implements RecordTaskStatusPersistenceOutboundPort {

    private final ActivityService activityService;
    private final EntityInitAndDetachService entityInitAndDetachService;

    RecordTaskStatusPersistenceOutboundPortImpl(ActivityService activityService,
                                                EntityInitAndDetachService entityInitAndDetachService) {
        this.activityService = activityService;
        this.entityInitAndDetachService = entityInitAndDetachService;
    }

    @Override
    public List<Activity> findOpenActivities(String dataProductFqn, String dataProductVersionNumber, String activityName) {
        ActivitySearchOptions options = new ActivitySearchOptions();
        options.setDataProductFqn(dataProductFqn);
        options.setDataProductVersionNumber(dataProductVersionNumber);
        options.setName(activityName);
        options.setStatuses(EnumSet.of(ExecutionStatus.PENDING, ExecutionStatus.RUNNING));
        return activityService.findAllFiltered(Pageable.unpaged(), options).getContent();
    }

    @Override
    public Activity findActivity(String uuid) {
        return activityService.findOne(uuid);
    }

    @Override
    public Activity findForUpdate(String uuid) {
        Activity activity = activityService.findOne(uuid);
        entityInitAndDetachService.refresh(activity);
        return activity;
    }

    @Override
    public List<Activity> findActivitiesByTaskProviderRunId(String providerRunId) {
        ActivitySearchOptions options = new ActivitySearchOptions();
        options.setTaskProviderRunId(providerRunId);
        options.setStatuses(EnumSet.of(ExecutionStatus.RUNNING));
        List<Activity> loaded = new ArrayList<>();
        for (Activity match : activityService.findAllFiltered(Pageable.unpaged(), options).getContent()) {
            loaded.add(findForUpdate(match.getUuid()));
        }
        return loaded;
    }

    @Override
    public Activity save(Activity activity) {
        return activityService.overwrite(activity.getUuid(), activity);
    }
}
