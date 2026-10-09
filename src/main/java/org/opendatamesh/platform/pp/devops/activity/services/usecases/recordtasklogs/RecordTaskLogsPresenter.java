package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtasklogs;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

public interface RecordTaskLogsPresenter {
    void presentTaskLogsRecorded(Activity activity);
}
