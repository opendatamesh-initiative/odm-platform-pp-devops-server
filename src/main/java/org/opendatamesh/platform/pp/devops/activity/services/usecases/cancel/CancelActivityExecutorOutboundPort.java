package org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel;

import org.opendatamesh.platform.pp.devops.activity.entities.Task;

interface CancelActivityExecutorOutboundPort {
    CancelRunResult cancelRun(Task task);
}
