package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskstatus;

import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;

public record RecordTaskStatusCommand(String dataProductFqn,
                                      String dataProductVersionTag,
                                      String activityName,
                                      String taskName,
                                      ExecutionStatus status) {
}
