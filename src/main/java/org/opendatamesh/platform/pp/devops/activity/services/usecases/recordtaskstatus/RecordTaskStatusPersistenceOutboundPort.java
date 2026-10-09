package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskstatus;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

interface RecordTaskStatusPersistenceOutboundPort {
    Activity findOpenActivity(String dataProductFqn, String dataProductVersionTag, String activityName);

    Activity findActivity(String uuid);

    Activity save(Activity activity);
}
