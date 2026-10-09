package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.execute;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;

@Schema(name = "ActivityExecuteCommand", description = "Request to create an activity and ask for its execution")
public class ActivityExecuteCommandRes {

    @Schema(description = "Activity to create and execute, including its tasks")
    private ActivityRes activity;

    public ActivityExecuteCommandRes() {
    }

    public ActivityRes getActivity() {
        return activity;
    }

    public void setActivity(ActivityRes activity) {
        this.activity = activity;
    }
}
