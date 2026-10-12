package org.opendatamesh.platform.pp.devops.activity.services.usecases.executetask;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

interface ExecuteTaskPersistenceOutboundPort {
    Activity findForUpdate(String uuid);

    Activity save(Activity activity);
}
