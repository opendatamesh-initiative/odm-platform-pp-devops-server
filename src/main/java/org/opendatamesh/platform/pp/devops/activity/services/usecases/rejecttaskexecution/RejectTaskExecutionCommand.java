package org.opendatamesh.platform.pp.devops.activity.services.usecases.rejecttaskexecution;

public record RejectTaskExecutionCommand(String activityUuid, String taskUuid) {
}
