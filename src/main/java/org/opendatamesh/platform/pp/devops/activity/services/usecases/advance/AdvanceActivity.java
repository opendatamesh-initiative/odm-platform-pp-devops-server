package org.opendatamesh.platform.pp.devops.activity.services.usecases.advance;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.exceptions.InternalException;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Full-control happy path. Brackets run only while the Policy service is inactive.
 * <pre>
 * ExecuteActivity
 *   → Activity Execution Requested
 *   → [auto-approve activity]
 *   → ApproveActivityExecution
 *   → AdvanceActivity
 *   → Task Execution Requested
 *   → [auto-approve task]
 *   → ExecuteTask
 *   → AdvanceActivity
 * </pre>
 * This class is {@code AdvanceActivity}. It requests the next pending task, or closes the activity.
 */
class AdvanceActivity implements UseCase {

    private enum Outcome {
        CLOSED,
        TASK_REQUESTED,
        NOTHING_TO_DO
    }

    private record AdvanceWork(Outcome outcome, Activity activity) {
    }

    private final AdvanceActivityCommand command;
    private final AdvanceActivityPresenter presenter;
    private final AdvanceActivityPersistenceOutboundPort persistencePort;
    private final AdvanceActivityNotificationOutboundPort notificationPort;
    private final AdvanceActivitySecretsOutboundPort secretsPort;
    private final TransactionalOutboundPort transactionalPort;

    AdvanceActivity(AdvanceActivityCommand command,
                    AdvanceActivityPresenter presenter,
                    AdvanceActivityPersistenceOutboundPort persistencePort,
                    AdvanceActivityNotificationOutboundPort notificationPort,
                    AdvanceActivitySecretsOutboundPort secretsPort,
                    TransactionalOutboundPort transactionalPort) {
        this.command = command;
        this.presenter = presenter;
        this.persistencePort = persistencePort;
        this.notificationPort = notificationPort;
        this.secretsPort = secretsPort;
        this.transactionalPort = transactionalPort;
    }

    @Override
    public void execute() {
        validateCommand();
        AdvanceWork work = advanceRunningActivity();
        if (work.outcome() == Outcome.CLOSED) {
            secretsPort.removeExecutorSecrets(work.activity());
        }
        presenter.presentActivityAdvanced(work.activity());
    }

    private void validateCommand() {
        if (command == null || !StringUtils.hasText(command.activityUuid())) {
            throw new BadRequestException("Activity UUID is required");
        }
    }

    private AdvanceWork advanceRunningActivity() {
        return transactionalPort.doInTransactionWithResults(ignored -> {
            Activity activity = persistencePort.findActivity(command.activityUuid());
            requireRunning(activity);
            List<Task> tasks = tasksInSortOrder(activity);
            if (anyTaskFailed(tasks)) {
                cancelPendingTasks(tasks);
                activity.setStatus(ExecutionStatus.FAILED);
                activity.setFinishedAt(now());
                persistencePort.save(activity);
                notificationPort.emitActivityFailed(activity);
                return new AdvanceWork(Outcome.CLOSED, activity);
            }
            if (allTasksSucceeded(tasks)) {
                activity.setStatus(ExecutionStatus.SUCCEEDED);
                activity.setFinishedAt(now());
                persistencePort.save(activity);
                notificationPort.emitActivitySucceeded(activity);
                return new AdvanceWork(Outcome.CLOSED, activity);
            }
            if (anyTaskRunning(tasks)) {
                return new AdvanceWork(Outcome.NOTHING_TO_DO, activity);
            }
            notificationPort.emitTaskExecutionRequested(activity, firstPendingTask(activity, tasks));
            return new AdvanceWork(Outcome.TASK_REQUESTED, activity);
        }, null);
    }

    private void requireRunning(Activity activity) {
        if (activity.getStatus() != ExecutionStatus.RUNNING) {
            throw new BadRequestException("Activity " + activity.getUuid() + " can be advanced only if RUNNING");
        }
    }

    private List<Task> tasksInSortOrder(Activity activity) {
        List<Task> tasks = new ArrayList<>();
        if (activity.getTasks() != null) {
            tasks.addAll(activity.getTasks());
        }
        tasks.sort(Comparator.comparing(Task::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())));
        return tasks;
    }

    private boolean anyTaskFailed(List<Task> tasks) {
        return tasks.stream().anyMatch(task -> task.getStatus() == ExecutionStatus.FAILED);
    }

    private boolean allTasksSucceeded(List<Task> tasks) {
        return tasks.stream().allMatch(task -> task.getStatus() == ExecutionStatus.SUCCEEDED);
    }

    private boolean anyTaskRunning(List<Task> tasks) {
        return tasks.stream().anyMatch(task -> task.getStatus() == ExecutionStatus.RUNNING);
    }

    private void cancelPendingTasks(List<Task> tasks) {
        Timestamp finishedAt = now();
        for (Task task : tasks) {
            if (task.getStatus() == ExecutionStatus.PENDING) {
                task.setStatus(ExecutionStatus.CANCELED);
                task.setFinishedAt(finishedAt);
            }
        }
    }

    private Task firstPendingTask(Activity activity, List<Task> tasks) {
        return tasks.stream()
                .filter(task -> task.getStatus() == ExecutionStatus.PENDING)
                .findFirst()
                .orElseThrow(() -> new InternalException("Activity " + activity.getUuid() + " has no pending task to request"));
    }

    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }
}
