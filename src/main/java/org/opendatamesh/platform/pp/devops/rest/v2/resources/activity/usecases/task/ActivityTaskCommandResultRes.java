package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.task;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;

@Schema(name = "ActivityTaskCommandResult", description = "The activity after a task command")
public class ActivityTaskCommandResultRes {

    @Schema(description = "The activity after the command")
    private ActivityRes activity;

    public ActivityTaskCommandResultRes() {
    }

    public ActivityTaskCommandResultRes(ActivityRes activity) {
        this.activity = activity;
    }

    public ActivityRes getActivity() {
        return activity;
    }

    public void setActivity(ActivityRes activity) {
        this.activity = activity;
    }
}
