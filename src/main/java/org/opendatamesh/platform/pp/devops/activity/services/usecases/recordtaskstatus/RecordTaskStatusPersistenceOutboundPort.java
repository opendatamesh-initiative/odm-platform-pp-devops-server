package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskstatus;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

import java.util.List;

interface RecordTaskStatusPersistenceOutboundPort {
    List<Activity> findOpenActivities(String dataProductFqn, String dataProductVersionNumber, String activityName);

    Activity findActivity(String uuid);

    Activity findForUpdate(String uuid);

    /**
     * Returns the RUNNING activities that have a task with the given provider run id, each loaded for update.
     * The caller passes a non-blank id: a blank one would not filter by run id.
     */
    List<Activity> findActivitiesByTaskProviderRunId(String providerRunId);

    Activity save(Activity activity);
}
