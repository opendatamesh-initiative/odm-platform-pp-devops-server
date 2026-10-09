package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskstatus;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Records the last status of a running instrumented task, then calls Advance Activity.
 * The CLI addresses the activity by data product, version tag, and activity name.
 */
class RecordTaskStatus implements UseCase {

    private final RecordTaskStatusCommand command;
    private final RecordTaskStatusPresenter presenter;
    private final RecordTaskStatusPersistenceOutboundPort persistencePort;
    private final RecordTaskStatusExecutorOutboundPort executorPort;
    private final RecordTaskStatusAdvanceActivityOutboundPort advancePort;
    private final TransactionalOutboundPort transactionalPort;

    private String dataProductFqn;
    private String dataProductVersionTag;
    private String activityName;
    private String taskName;
    private ExecutionStatus status;
    private String activityUuid;

    RecordTaskStatus(RecordTaskStatusCommand command,
                     RecordTaskStatusPresenter presenter,
                     RecordTaskStatusPersistenceOutboundPort persistencePort,
                     RecordTaskStatusExecutorOutboundPort executorPort,
                     RecordTaskStatusAdvanceActivityOutboundPort advancePort,
                     TransactionalOutboundPort transactionalPort) {
        this.command = command;
        this.presenter = presenter;
        this.persistencePort = persistencePort;
        this.executorPort = executorPort;
        this.advancePort = advancePort;
        this.transactionalPort = transactionalPort;
    }

    @Override
    public void execute() {
        validateCommand();
        recordTerminalStatus();
        advancePort.advanceActivity(activityUuid);
        presentAdvancedActivity();
    }

    private void validateCommand() {
        dataProductFqn = command == null ? null : trimToNull(command.dataProductFqn());
        dataProductVersionTag = command == null ? null : trimToNull(command.dataProductVersionTag());
        activityName = command == null ? null : trimToNull(command.activityName());
        taskName = command == null ? null : trimToNull(command.taskName());
        status = command == null ? null : command.status();
        if (!StringUtils.hasText(dataProductFqn)) {
            throw new BadRequestException("Data product FQN is required");
        }
        if (!StringUtils.hasText(dataProductVersionTag)) {
            throw new BadRequestException("Data product version tag is required");
        }
        if (!StringUtils.hasText(activityName)) {
            throw new BadRequestException("Activity name is required");
        }
        if (!StringUtils.hasText(taskName)) {
            throw new BadRequestException("Task name is required");
        }
        if (status != ExecutionStatus.SUCCEEDED && status != ExecutionStatus.FAILED && status != ExecutionStatus.CANCELED) {
            throw new BadRequestException("Task status must be SUCCEEDED, FAILED, or CANCELED");
        }
    }

    private void recordTerminalStatus() {
        activityUuid = transactionalPort.doInTransactionWithResults(ignored -> {
            Activity activity = persistencePort.findOpenActivity(dataProductFqn, dataProductVersionTag, activityName);
            Task task = resolveTask(activity);
            requireInstrumented(task);
            requireRunning(task);
            task.setStatus(status);
            task.setFinishedAt(new Timestamp(System.currentTimeMillis()));
            return persistencePort.save(activity).getUuid();
        }, null);
    }

    private void presentAdvancedActivity() {
        transactionalPort.doInTransaction(() ->
                presenter.presentTaskStatusRecorded(persistencePort.findActivity(activityUuid))
        );
    }

    private void requireInstrumented(Task task) {
        if (executorPort.findExecutionMode(task.getExecutorName()) != ExecutionMode.INSTRUMENTED) {
            throw new BadRequestException("Task " + taskName + " is not instrumented");
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
