package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.cancel;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;

@Schema(name = "ActivityCancelResult", description = "The activity after cancel has finished")
public class ActivityCancelResultRes {

    @Schema(description = "The activity, succeeded, failed, or canceled")
    private ActivityRes activity;

    public ActivityCancelResultRes() {
    }

    public ActivityCancelResultRes(ActivityRes activity) {
        this.activity = activity;
    }

    public ActivityRes getActivity() {
        return activity;
    }

    public void setActivity(ActivityRes activity) {
        this.activity = activity;
    }
}
