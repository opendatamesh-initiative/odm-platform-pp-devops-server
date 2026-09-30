package org.opendatamesh.platform.pp.devops.activity.services.usecases.advance;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;

interface AdvanceActivityNotificationOutboundPort {
    void emitTaskExecutionRequested(Activity activity, Task task);

    void emitActivitySucceeded(Activity activity);

    void emitActivityFailed(Activity activity);
}
