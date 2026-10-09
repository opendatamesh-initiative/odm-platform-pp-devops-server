package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtasklogs;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

interface RecordTaskLogsPersistenceOutboundPort {
    Activity findOpenActivity(String dataProductFqn, String dataProductVersionTag, String activityName);

    Activity save(Activity activity);
}
