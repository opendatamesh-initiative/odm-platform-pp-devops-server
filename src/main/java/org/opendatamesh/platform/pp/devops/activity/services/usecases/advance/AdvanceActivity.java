package org.opendatamesh.platform.pp.devops.activity.services.usecases.advance;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.exceptions.InternalException;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
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
 * This class is {@code AdvanceActivity}. It emits task execution requested only when the next pending task is full control.
 * An instrumented next task stays pending. This class does not skip that task to start a later full-control task.
 * The CLI requests that instrumented task, and a terminal status then calls this class again.
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
    private final AdvanceActivityExecutorOutboundPort executorPort;
    private final AdvanceActivityNotificationOutboundPort notificationPort;
    private final AdvanceActivitySecretsOutboundPort secretsPort;
    private final TransactionalOutboundPort transactionalPort;

    AdvanceActivity(AdvanceActivityCommand command,
                    AdvanceActivityPresenter presenter,
                    AdvanceActivityPersistenceOutboundPort persistencePort,
                    AdvanceActivityExecutorOutboundPort executorPort,
                    AdvanceActivityNotificationOutboundPort notificationPort,
                    AdvanceActivitySecretsOutboundPort secretsPort,
                    TransactionalOutboundPort transactionalPort) {
        this.command = command;
        this.presenter = presenter;
        this.persistencePort = persistencePort;
        this.executorPort = executorPort;
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
            List<Task> tasks = tasksInSortOrder(activity);
            if (isTerminal(activity.getStatus())) {
                return new AdvanceWork(Outcome.NOTHING_TO_DO, activity);
            }
            if (anyTaskFailed(tasks)) {
                closeFailed(activity, tasks);
                return new AdvanceWork(Outcome.CLOSED, activity);
            }
            if (anyTaskRunning(tasks)) {
                return new AdvanceWork(Outcome.NOTHING_TO_DO, activity);
            }
            if (anyTaskCanceled(tasks)) {
                closeCanceled(activity, tasks);
                return new AdvanceWork(Outcome.CLOSED, activity);
            }
            if (allTasksSucceeded(tasks)) {
                closeSucceeded(activity);
                return new AdvanceWork(Outcome.CLOSED, activity);
            }
            if (activity.getStatus() == ExecutionStatus.RUNNING && anyTaskPending(tasks)) {
                return requestNextTaskIfFullControl(activity, tasks);
            }
            return new AdvanceWork(Outcome.NOTHING_TO_DO, activity);
        }, null);
    }

    private void closeFailed(Activity activity, List<Task> tasks) {
        cancelPendingTasks(tasks);
        activity.setStatus(ExecutionStatus.FAILED);
        activity.setFinishedAt(now());
        persistencePort.save(activity);
        notificationPort.emitActivityFailed(activity);
    }

    private void closeCanceled(Activity activity, List<Task> tasks) {
        cancelPendingTasks(tasks);
        activity.setStatus(ExecutionStatus.CANCELED);
        activity.setFinishedAt(now());
        persistencePort.save(activity);
        notificationPort.emitActivityCanceled(activity);
    }

    private void closeSucceeded(Activity activity) {
        activity.setStatus(ExecutionStatus.SUCCEEDED);
        activity.setFinishedAt(now());
        persistencePort.save(activity);
        notificationPort.emitActivitySucceeded(activity);
    }

    private static boolean isTerminal(ExecutionStatus status) {
        return status == ExecutionStatus.SUCCEEDED
                || status == ExecutionStatus.FAILED
                || status == ExecutionStatus.CANCELED;
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

    private boolean anyTaskCanceled(List<Task> tasks) {
        return tasks.stream().anyMatch(task -> task.getStatus() == ExecutionStatus.CANCELED);
    }

    private boolean anyTaskPending(List<Task> tasks) {
        return tasks.stream().anyMatch(task -> task.getStatus() == ExecutionStatus.PENDING);
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

    private AdvanceWork requestNextTaskIfFullControl(Activity activity, List<Task> tasks) {
        Task pending = firstPendingTask(activity, tasks);
        if (executorPort.findExecutionMode(pending.getExecutorName()) == ExecutionMode.FULL_CONTROL) {
            notificationPort.emitTaskExecutionRequested(activity, pending);
            return new AdvanceWork(Outcome.TASK_REQUESTED, activity);
        }
        return new AdvanceWork(Outcome.NOTHING_TO_DO, activity);
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
