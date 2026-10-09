package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskstatus;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

public interface RecordTaskStatusPresenter {
    void presentTaskStatusRecorded(Activity activity);
}
