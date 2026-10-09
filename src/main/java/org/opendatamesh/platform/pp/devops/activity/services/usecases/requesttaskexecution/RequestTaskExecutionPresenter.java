package org.opendatamesh.platform.pp.devops.activity.services.usecases.requesttaskexecution;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

public interface RequestTaskExecutionPresenter {
    void presentTaskExecutionRequested(Activity activity);
}
