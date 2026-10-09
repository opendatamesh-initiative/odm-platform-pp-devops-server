package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.cancel;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;

@Schema(name = "ActivityCancelCommand", description = "Request to cancel an open activity")
public class ActivityCancelCommandRes {

    @Schema(description = "Activity to cancel. Only the uuid is read")
    private ActivityRes activity;

    public ActivityCancelCommandRes() {
    }

    public ActivityRes getActivity() {
        return activity;
    }

    public void setActivity(ActivityRes activity) {
        this.activity = activity;
    }
}
