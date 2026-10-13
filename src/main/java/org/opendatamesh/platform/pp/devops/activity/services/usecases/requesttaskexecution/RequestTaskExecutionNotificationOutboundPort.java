package org.opendatamesh.platform.pp.devops.activity.services.usecases.requesttaskexecution;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;

interface RequestTaskExecutionNotificationOutboundPort {
    void emitTaskExecutionRequested(Activity activity, Task task);
}
