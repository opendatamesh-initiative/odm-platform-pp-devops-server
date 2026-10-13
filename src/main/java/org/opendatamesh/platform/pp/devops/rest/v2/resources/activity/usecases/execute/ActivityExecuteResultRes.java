package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.execute;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;

@Schema(name = "ActivityExecuteResult", description = "The activity created by an execute request")
public class ActivityExecuteResultRes {

    @Schema(description = "The created activity, pending execution")
    private ActivityRes activity;

    public ActivityExecuteResultRes() {
    }

    public ActivityExecuteResultRes(ActivityRes activity) {
        this.activity = activity;
    }

    public ActivityRes getActivity() {
        return activity;
    }

    public void setActivity(ActivityRes activity) {
        this.activity = activity;
    }
}
