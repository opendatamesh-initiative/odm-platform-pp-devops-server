package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskresults;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

interface RecordTaskResultsPersistenceOutboundPort {
    Activity findActivity(String uuid);

    Activity findOpenActivity(String dataProductFqn, String dataProductVersionTag, String activityName);

    Activity save(Activity activity);
}
