package org.opendatamesh.platform.pp.devops.activity.services.usecases.rejectexecution;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

interface RejectActivityExecutionNotificationOutboundPort {
    void emitActivityFailed(Activity activity);
}
