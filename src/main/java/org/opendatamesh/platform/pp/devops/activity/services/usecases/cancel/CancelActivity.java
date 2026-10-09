package org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.exceptions.InternalException;
import org.opendatamesh.platform.pp.devops.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cancels every pending task. A running full-control task is asked to stop on its executor.
 * A running instrumented task is left running and is not sent to an executor.
 * When any running task is instrumented, this cancel does not call Advance Activity.
 * Record Task Status does, and this class only reads until the activity is terminal.
 * When every running task is full control, a task that has already left running may advance the activity,
 * and the wait still advances an open activity that has no running task.
 */
class CancelActivity implements UseCase {

    private static final Duration PROVIDER_RUN_ID_DEADLINE = Duration.ofSeconds(300);

    private final CancelActivityCommand command;
    private final CancelActivityPresenter presenter;
    private final CancelActivityPersistenceOutboundPort persistencePort;
    private final CancelActivityAdvanceActivityOutboundPort advancePort;
    private final CancelActivityExecutorOutboundPort executorPort;
    private final CancelActivityPollingOutboundPort pollingPort;
    private final TransactionalOutboundPort transactionalPort;

    CancelActivity(CancelActivityCommand command,
                   CancelActivityPresenter presenter,
                   CancelActivityPersistenceOutboundPort persistencePort,
                   CancelActivityAdvanceActivityOutboundPort advancePort,
                   CancelActivityExecutorOutboundPort executorPort,
                   CancelActivityPollingOutboundPort pollingPort,
                   TransactionalOutboundPort transactionalPort) {
        this.command = command;
        this.presenter = presenter;
        this.persistencePort = persistencePort;
        this.advancePort = advancePort;
        this.executorPort = executorPort;
        this.pollingPort = pollingPort;
        this.transactionalPort = transactionalPort;
    }

    @Override
    public void execute() {
        validateCommand();
        List<String> runningTaskUuids = cancelTasksThatHaveNotStarted();
        if (runningTaskUuids.isEmpty()) {
            closeAndPresent();
            return;
        }
        List<ClassifiedRunningTask> runningTasks = classifyRunningTasks(runningTaskUuids);
        CancelClose close = runningTasks.stream().anyMatch(task -> task.kind() == RunningTaskKind.INSTRUMENTED)
                ? CancelClose.RECORD_TASK_STATUS_CLOSES
                : CancelClose.CANCEL_MAY_ADVANCE;
        for (ClassifiedRunningTask task : runningTasks) {
            stopRunningTask(task, close);
        }
        waitUntilActivityIsTerminal(close);
    }

    private void validateCommand() {
        if (command == null || !StringUtils.hasText(command.activityUuid())) {
            throw new BadRequestException("Activity UUID is required");
        }
    }

    private List<String> cancelTasksThatHaveNotStarted() {
        return transactionalPort.doInTransactionWithResults(ignored -> {
            Activity activity = persistencePort.findActivity(command.activityUuid());
            refuseWhenAlreadyTerminated(activity);
            Timestamp finishedAt = now();
            List<String> runningTaskUuids = new ArrayList<>();
            for (Task task : tasksInSortOrder(activity)) {
                if (task.getStatus() == ExecutionStatus.PENDING) {
                    task.setStatus(ExecutionStatus.CANCELED);
                    task.setFinishedAt(finishedAt);
                } else if (task.getStatus() == ExecutionStatus.RUNNING) {
                    runningTaskUuids.add(task.getUuid());
                }
            }
            persistencePort.save(activity);
            return runningTaskUuids;
        }, null);
    }

    private void refuseWhenAlreadyTerminated(Activity activity) {
        if (isTerminal(activity.getStatus())) {
            throw new BadRequestException(
                    "Activity " + activity.getUuid() + " has already terminated with status " + activity.getStatus()
            );
        }
    }

    private void closeAndPresent() {
        advancePort.advanceActivity(command.activityUuid());
        presenter.presentCancelCompleted(loadActivity(command.activityUuid()));
    }

    private List<ClassifiedRunningTask> classifyRunningTasks(List<String> taskUuids) {
        List<ClassifiedRunningTask> classified = new ArrayList<>();
        for (String taskUuid : taskUuids) {
            TaskSnapshot task = readTask(taskUuid);
            RunningTaskKind kind = executorPort.findExecutionMode(task.executorName()) == ExecutionMode.INSTRUMENTED
                    ? RunningTaskKind.INSTRUMENTED
                    : RunningTaskKind.FULL_CONTROL;
            classified.add(new ClassifiedRunningTask(taskUuid, kind));
        }
        return classified;
    }

    private void stopRunningTask(ClassifiedRunningTask task, CancelClose close) {
        switch (task.kind()) {
            case INSTRUMENTED -> leaveInstrumentedTaskRunning();
            case FULL_CONTROL -> stopFullControlTask(task.taskUuid(), close);
        }
    }

    private void leaveInstrumentedTaskRunning() {
        // The task stays RUNNING. This cancel does not open an executor client and does not advance.
    }

    private void stopFullControlTask(String taskUuid, CancelClose close) {
        TaskSnapshot task = waitForProviderRunId(taskUuid);
        if (task.status() == ExecutionStatus.RUNNING && !StringUtils.hasText(task.providerRunId())) {
            throw new InternalException("Activity " + command.activityUuid() + " running task could not be canceled");
        }
        if (task.status() == ExecutionStatus.RUNNING) {
            CancelRunResult result = executorPort.cancelRun(task.asTask(command.activityUuid()));
            if (result == CancelRunResult.UNREACHABLE) {
                throw new InternalException("Activity " + command.activityUuid() + " running task could not be canceled");
            }
            return;
        }
        if (close == CancelClose.CANCEL_MAY_ADVANCE && !isTerminal(loadActivity(command.activityUuid()).getStatus())) {
            advancePort.advanceActivity(command.activityUuid());
        }
    }

    private TaskSnapshot waitForProviderRunId(String taskUuid) {
        long deadline = System.nanoTime() + PROVIDER_RUN_ID_DEADLINE.toNanos();
        TaskSnapshot task = readTask(taskUuid);
        while (task.status() == ExecutionStatus.RUNNING
                && !StringUtils.hasText(task.providerRunId())
                && System.nanoTime() < deadline) {
            pollingPort.waitOneSecond();
            task = readTask(taskUuid);
        }
        return task;
    }

    private void waitUntilActivityIsTerminal(CancelClose close) {
        int max = pollingPort.maxStatusReads();
        for (int read = 1; read <= max; read++) {
            Activity activity = loadActivity(command.activityUuid());
            if (isTerminal(activity.getStatus())) {
                presenter.presentCancelCompleted(activity);
                return;
            }
            if (close == CancelClose.CANCEL_MAY_ADVANCE && !anyTaskRunning(activity) && isOpen(activity.getStatus())) {
                advancePort.advanceActivity(command.activityUuid());
                activity = loadActivity(command.activityUuid());
                if (isTerminal(activity.getStatus())) {
                    presenter.presentCancelCompleted(activity);
                    return;
                }
            }
            if (read < max) {
                pollingPort.waitOnePollInterval();
            }
        }
        throw new InternalException("Activity " + command.activityUuid() + " did not reach a terminal status");
    }

    private Activity loadActivity(String activityUuid) {
        return persistencePort.findDetached(activityUuid);
    }

    private TaskSnapshot readTask(String taskUuid) {
        Activity activity = loadActivity(command.activityUuid());
        Task task = findTask(activity, taskUuid);
        return new TaskSnapshot(task.getUuid(), task.getExecutorName(), task.getProviderRunId(), task.getStatus());
    }

    private Task findTask(Activity activity, String taskUuid) {
        if (activity.getTasks() != null) {
            for (Task task : activity.getTasks()) {
                if (taskUuid.equals(task.getUuid())) {
                    return task;
                }
            }
        }
        throw new NotFoundException("Task " + taskUuid + " not found in activity " + activity.getUuid());
    }

    private List<Task> tasksInSortOrder(Activity activity) {
        List<Task> tasks = new ArrayList<>();
        if (activity.getTasks() != null) {
            tasks.addAll(activity.getTasks());
        }
        tasks.sort(Comparator.comparing(Task::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())));
        return tasks;
    }

    private static boolean anyTaskRunning(Activity activity) {
        if (activity.getTasks() == null) {
            return false;
        }
        return activity.getTasks().stream().anyMatch(task -> task.getStatus() == ExecutionStatus.RUNNING);
    }

    private static boolean isTerminal(ExecutionStatus status) {
        return status == ExecutionStatus.SUCCEEDED
                || status == ExecutionStatus.FAILED
                || status == ExecutionStatus.CANCELED;
    }

    private static boolean isOpen(ExecutionStatus status) {
        return status == ExecutionStatus.PENDING || status == ExecutionStatus.RUNNING;
    }

    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }

    private enum RunningTaskKind {
        INSTRUMENTED,
        FULL_CONTROL
    }

    private enum CancelClose {
        RECORD_TASK_STATUS_CLOSES,
        CANCEL_MAY_ADVANCE
    }

    private record ClassifiedRunningTask(String taskUuid, RunningTaskKind kind) {
    }

    private record TaskSnapshot(String taskUuid, String executorName, String providerRunId, ExecutionStatus status) {
        private Task asTask(String activityUuid) {
            Activity activity = new Activity();
            activity.setUuid(activityUuid);
            Task task = new Task();
            task.setUuid(taskUuid);
            task.setExecutorName(executorName);
            task.setProviderRunId(providerRunId);
            task.setStatus(status);
            task.setActivity(activity);
            return task;
        }
    }
}
