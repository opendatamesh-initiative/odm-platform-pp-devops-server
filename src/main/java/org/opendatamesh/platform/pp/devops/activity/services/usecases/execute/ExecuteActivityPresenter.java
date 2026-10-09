package org.opendatamesh.platform.pp.devops.activity.services.usecases.execute;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

public interface ExecuteActivityPresenter {
    void presentActivityExecutionRequested(Activity activity);
}
