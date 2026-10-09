package org.opendatamesh.platform.pp.devops.activity.services.usecases.requesttaskexecution;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

interface RequestTaskExecutionPersistenceOutboundPort {
    Activity findOpenActivity(String dataProductFqn, String dataProductVersionTag, String activityName);

    Activity save(Activity activity);
}
