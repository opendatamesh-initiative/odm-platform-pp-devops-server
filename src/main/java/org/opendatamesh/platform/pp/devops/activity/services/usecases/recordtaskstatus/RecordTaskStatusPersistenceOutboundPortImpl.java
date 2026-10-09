package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskstatus;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivitySearchOptions;
import org.opendatamesh.platform.pp.devops.utils.services.EntityInitAndDetachService;
import org.springframework.data.domain.Pageable;

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
    public Activity findOpenActivity(String dataProductFqn, String dataProductVersionTag, String activityName) {
        ActivitySearchOptions options = new ActivitySearchOptions();
        options.setDataProductFqn(dataProductFqn);
        options.setDataProductVersionTag(dataProductVersionTag);
        options.setName(activityName);
        options.setStatuses(EnumSet.of(ExecutionStatus.PENDING, ExecutionStatus.RUNNING));
        List<Activity> matches = activityService.findAllFiltered(Pageable.unpaged(), options).getContent();
        if (matches.isEmpty()) {
            throw new NotFoundException("No PENDING or RUNNING activity " + activityName
                    + " for data product " + dataProductFqn + " version " + dataProductVersionTag);
        }
        if (matches.size() > 1) {
            throw new BadRequestException("More than one open activity " + activityName
                    + " for data product " + dataProductFqn + " version " + dataProductVersionTag);
        }
        return findActivity(matches.get(0).getUuid());
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
