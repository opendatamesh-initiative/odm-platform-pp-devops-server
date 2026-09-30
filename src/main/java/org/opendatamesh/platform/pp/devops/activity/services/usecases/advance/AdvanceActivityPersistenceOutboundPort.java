package org.opendatamesh.platform.pp.devops.activity.services.usecases.advance;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

interface AdvanceActivityPersistenceOutboundPort {
    Activity findActivity(String uuid);

    Activity save(Activity activity);
}
