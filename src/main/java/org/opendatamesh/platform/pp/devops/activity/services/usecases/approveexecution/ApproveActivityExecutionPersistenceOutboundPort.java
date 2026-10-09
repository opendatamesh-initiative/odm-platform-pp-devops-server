package org.opendatamesh.platform.pp.devops.activity.services.usecases.approveexecution;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

interface ApproveActivityExecutionPersistenceOutboundPort {
    Activity findActivity(String uuid);

    Activity save(Activity activity);
}
