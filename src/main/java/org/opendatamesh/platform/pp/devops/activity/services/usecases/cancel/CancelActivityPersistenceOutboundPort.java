package org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

interface CancelActivityPersistenceOutboundPort {
    Activity findActivity(String uuid);

    Activity save(Activity activity);

    Activity findDetached(String uuid);
}
