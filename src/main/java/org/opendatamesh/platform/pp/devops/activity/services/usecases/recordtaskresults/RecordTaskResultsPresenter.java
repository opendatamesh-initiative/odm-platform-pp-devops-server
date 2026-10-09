package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskresults;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

public interface RecordTaskResultsPresenter {
    void presentTaskResultsRecorded(Activity activity);
}
