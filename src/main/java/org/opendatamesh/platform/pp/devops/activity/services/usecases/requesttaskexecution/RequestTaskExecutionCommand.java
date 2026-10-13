package org.opendatamesh.platform.pp.devops.activity.services.usecases.requesttaskexecution;

public record RequestTaskExecutionCommand(String dataProductFqn,
                                          String dataProductVersionTag,
                                          String activityName,
                                          String taskName,
                                          String providerRunId) {
}
