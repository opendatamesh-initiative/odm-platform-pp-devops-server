package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskresults;

import org.opendatamesh.platform.pp.devops.activity.entities.TaskResult;

import java.util.List;

public record RecordTaskResultsCommand(String providerRunId,
                                       String activityUuid,
                                       String dataProductFqn,
                                       String dataProductVersionTag,
                                       String activityName,
                                       String taskName,
                                       List<TaskResult> results) {
}
