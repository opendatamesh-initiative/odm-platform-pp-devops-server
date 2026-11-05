package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtasklogs;

import org.opendatamesh.platform.pp.devops.activity.entities.TaskLog;

import java.util.List;

public record RecordTaskLogsCommand(String providerRunId,
                                    String dataProductFqn,
                                    String dataProductVersionNumber,
                                    String activityName,
                                    String taskName,
                                    List<TaskLog> logs) {
}
