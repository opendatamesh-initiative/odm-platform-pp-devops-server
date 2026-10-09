package org.opendatamesh.platform.pp.devops.activity.services.usecases.rejectexecution;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

interface RejectActivityExecutionPersistenceOutboundPort {
    Activity findActivity(String uuid);

    Activity save(Activity activity);
}
