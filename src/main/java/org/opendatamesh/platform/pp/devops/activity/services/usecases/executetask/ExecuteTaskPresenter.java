package org.opendatamesh.platform.pp.devops.activity.services.usecases.executetask;

import org.opendatamesh.platform.pp.devops.activity.entities.Task;

interface ExecuteTaskPresenter {
    void presentTaskExecuted(Task task);
}
