package org.opendatamesh.platform.pp.devops.activity.services.usecases.rejecttaskexecution;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

interface RejectTaskExecutionPersistenceOutboundPort {
    Activity findForUpdate(String uuid);

    Activity save(Activity activity);
}
