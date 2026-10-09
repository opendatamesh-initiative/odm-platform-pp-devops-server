package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskresults;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskResult;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Appends result rows to a running task of either execution mode.
 * The caller may send the activity uuid, or the data product, version tag, and activity name.
 */
class RecordTaskResults implements UseCase {

    private final RecordTaskResultsCommand command;
    private final RecordTaskResultsPresenter presenter;
    private final RecordTaskResultsPersistenceOutboundPort persistencePort;
    private final TransactionalOutboundPort transactionalPort;

    private String activityUuid;
    private String dataProductFqn;
    private String dataProductVersionTag;
    private String activityName;
    private String taskName;
    private List<TaskResult> results;

    RecordTaskResults(RecordTaskResultsCommand command,
                      RecordTaskResultsPresenter presenter,
                      RecordTaskResultsPersistenceOutboundPort persistencePort,
                      TransactionalOutboundPort transactionalPort) {
        this.command = command;
        this.presenter = presenter;
        this.persistencePort = persistencePort;
        this.transactionalPort = transactionalPort;
    }

    @Override
    public void execute() {
        validateCommand();
        appendResults();
    }

    private void validateCommand() {
        activityUuid = command == null ? null : trimToNull(command.activityUuid());
        dataProductFqn = command == null ? null : trimToNull(command.dataProductFqn());
        dataProductVersionTag = command == null ? null : trimToNull(command.dataProductVersionTag());
        activityName = command == null ? null : trimToNull(command.activityName());
        taskName = command == null ? null : trimToNull(command.taskName());
        requireActivityKey();
        if (!StringUtils.hasText(taskName)) {
            throw new BadRequestException("Task name is required");
        }
        results = command.results();
        if (results == null || results.isEmpty()) {
            throw new BadRequestException("At least one task result is required");
        }
        for (TaskResult result : results) {
            normalize(result);
        }
    }

    private void normalize(TaskResult result) {
        if (result == null) {
            throw new BadRequestException("Task result content is required");
        }
        String content = result.getContent() == null ? null : result.getContent().trim();
        if (!StringUtils.hasText(content)) {
            throw new BadRequestException("Task result content is required");
        }
        result.setContent(content);
        result.setUuid(null);
        if (result.getGeneratedAt() == null) {
            result.setGeneratedAt(new Timestamp(System.currentTimeMillis()));
        }
    }

    private void appendResults() {
        transactionalPort.doInTransaction(() -> {
            Activity activity = StringUtils.hasText(activityUuid)
                    ? persistencePort.findActivity(activityUuid)
                    : persistencePort.findOpenActivity(dataProductFqn, dataProductVersionTag, activityName);
            Task task = resolveTask(activity);
            requireRunning(task);
            appendTo(task);
            presenter.presentTaskResultsRecorded(persistencePort.save(activity));
        });
    }

    private void appendTo(Task task) {
        if (task.getResults() == null) {
            task.setResults(new ArrayList<>());
        }
        for (TaskResult result : results) {
            result.setTask(task);
            task.getResults().add(result);
        }
    }

    private void requireRunning(Task task) {
        if (task.getStatus() != ExecutionStatus.RUNNING) {
            throw new BadRequestException("Task " + taskName + " is not RUNNING");
        }
    }

    private Task resolveTask(Activity activity) {
        List<Task> matches = new ArrayList<>();
        if (activity.getTasks() != null) {
            for (Task task : activity.getTasks()) {
                if (taskName.equals(task.getName())) {
                    matches.add(task);
                }
            }
        }
        if (matches.isEmpty()) {
            throw new BadRequestException("Task " + taskName + " not found in activity " + activity.getUuid());
        }
        if (matches.size() > 1) {
            throw new BadRequestException("Task " + taskName + " matches more than one task in activity " + activity.getUuid());
        }
        return matches.get(0);
    }

    private void requireActivityKey() {
        boolean hasNaturalKey = StringUtils.hasText(dataProductFqn)
                || StringUtils.hasText(dataProductVersionTag)
                || StringUtils.hasText(activityName);
        if (!StringUtils.hasText(activityUuid) && !hasNaturalKey) {
            throw new BadRequestException(
                    "Activity UUID or data product FQN, version tag, and activity name are required"
            );
        }
        if (StringUtils.hasText(activityUuid)) {
            return;
        }
        if (!StringUtils.hasText(dataProductFqn)) {
            throw new BadRequestException("Data product FQN is required");
        }
        if (!StringUtils.hasText(dataProductVersionTag)) {
            throw new BadRequestException("Data product version tag is required");
        }
        if (!StringUtils.hasText(activityName)) {
            throw new BadRequestException("Activity name is required");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
