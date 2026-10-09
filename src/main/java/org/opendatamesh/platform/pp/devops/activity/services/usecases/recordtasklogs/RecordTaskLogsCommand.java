package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtasklogs;

import org.opendatamesh.platform.pp.devops.activity.entities.TaskLog;

import java.util.List;

public record RecordTaskLogsCommand(String dataProductFqn,
                                    String dataProductVersionTag,
                                    String activityName,
                                    String taskName,
                                    List<TaskLog> logs) {
}
