package org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

public interface CancelActivityPresenter {
    void presentCancelCompleted(Activity activity);
}
