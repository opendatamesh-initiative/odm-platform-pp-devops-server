package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskresults;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

import java.util.List;

interface RecordTaskResultsPersistenceOutboundPort {
    Activity findForUpdate(String uuid);

    List<Activity> findOpenActivities(String dataProductFqn, String dataProductVersionTag, String activityName);

    List<Activity> findActivitiesWithRunningTask(String providerRunId);

    Activity save(Activity activity);
}
