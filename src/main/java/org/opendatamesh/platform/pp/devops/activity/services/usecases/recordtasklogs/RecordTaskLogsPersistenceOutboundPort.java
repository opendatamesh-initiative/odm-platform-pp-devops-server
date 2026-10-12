package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtasklogs;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

import java.util.List;

interface RecordTaskLogsPersistenceOutboundPort {
    List<Activity> findOpenActivities(String dataProductFqn, String dataProductVersionTag, String activityName);

    Activity findForUpdate(String uuid);

    Activity save(Activity activity);
}
