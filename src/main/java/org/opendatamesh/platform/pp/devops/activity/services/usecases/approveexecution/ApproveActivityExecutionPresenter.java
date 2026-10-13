package org.opendatamesh.platform.pp.devops.activity.services.usecases.approveexecution;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

interface ApproveActivityExecutionPresenter {
    void presentActivityExecutionApproved(Activity activity);
}
