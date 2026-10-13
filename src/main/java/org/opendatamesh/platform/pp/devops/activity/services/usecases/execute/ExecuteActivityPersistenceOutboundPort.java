package org.opendatamesh.platform.pp.devops.activity.services.usecases.execute;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;

import java.util.List;
import java.util.Set;

interface ExecuteActivityPersistenceOutboundPort {
    List<Activity> findActivities(String dataProductVersionUuid, String activityName, Set<ExecutionStatus> statuses);

    Activity create(Activity activity);
}
