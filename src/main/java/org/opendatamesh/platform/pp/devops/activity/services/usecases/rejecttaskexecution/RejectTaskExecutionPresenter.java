package org.opendatamesh.platform.pp.devops.activity.services.usecases.rejecttaskexecution;

import org.opendatamesh.platform.pp.devops.activity.entities.Task;

interface RejectTaskExecutionPresenter {
    void presentTaskExecutionRejected(Task task);
}
