package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskstatus;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

import java.util.List;

interface RecordTaskStatusPersistenceOutboundPort {
    List<Activity> findOpenActivities(String dataProductFqn, String dataProductVersionTag, String activityName);

    Activity findActivity(String uuid);

    Activity findForUpdate(String uuid);

    Activity save(Activity activity);
}
