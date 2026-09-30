package org.opendatamesh.platform.pp.devops.activity.services.usecases.execute;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

interface ExecuteActivityNotificationOutboundPort {
    void emitActivityExecutionRequested(Activity activity);
}
