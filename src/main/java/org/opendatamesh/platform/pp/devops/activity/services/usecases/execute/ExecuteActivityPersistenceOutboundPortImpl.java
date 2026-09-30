package org.opendatamesh.platform.pp.devops.activity.services.usecases.execute;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;

import java.util.List;
import java.util.Set;

class ExecuteActivityPersistenceOutboundPortImpl implements ExecuteActivityPersistenceOutboundPort {

    private final ActivityService activityService;

    ExecuteActivityPersistenceOutboundPortImpl(ActivityService activityService) {
        this.activityService = activityService;
    }

    @Override
    public List<Activity> findActivities(String dataProductVersionUuid, String activityName, Set<ExecutionStatus> statuses) {
        return activityService.findActivitiesInStatus(dataProductVersionUuid, activityName, statuses);
    }

    @Override
    public Activity create(Activity activity) {
        return activityService.create(activity);
    }
}
