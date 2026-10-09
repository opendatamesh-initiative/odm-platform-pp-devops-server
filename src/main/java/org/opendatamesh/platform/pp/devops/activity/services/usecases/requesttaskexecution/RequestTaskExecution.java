package org.opendatamesh.platform.pp.devops.activity.services.usecases.requesttaskexecution;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The CLI asks for the next pending instrumented task.
 * It addresses the activity by data product, version tag, and activity name.
 * The provider run id is stored, task execution is requested, and the task stays pending.
 * A later task is refused while an earlier one is pending or while another task is running.
 */
class RequestTaskExecution implements UseCase {

    private final RequestTaskExecutionCommand command;
    private final RequestTaskExecutionPresenter presenter;
    private final RequestTaskExecutionPersistenceOutboundPort persistencePort;
    private final RequestTaskExecutionExecutorOutboundPort executorPort;
    private final RequestTaskExecutionNotificationOutboundPort notificationPort;
    private final TransactionalOutboundPort transactionalPort;

    private String dataProductFqn;
    private String dataProductVersionTag;
    private String activityName;
    private String taskName;
    private String providerRunId;

    RequestTaskExecution(RequestTaskExecutionCommand command,
                         RequestTaskExecutionPresenter presenter,
                         RequestTaskExecutionPersistenceOutboundPort persistencePort,
                         RequestTaskExecutionExecutorOutboundPort executorPort,
                         RequestTaskExecutionNotificationOutboundPort notificationPort,
                         TransactionalOutboundPort transactionalPort) {
        this.command = command;
        this.presenter = presenter;
        this.persistencePort = persistencePort;
        this.executorPort = executorPort;
        this.notificationPort = notificationPort;
        this.transactionalPort = transactionalPort;
    }

    @Override
    public void execute() {
        validateCommand();
        requestPendingTask();
    }

    private void validateCommand() {
        dataProductFqn = command == null ? null : trimToNull(command.dataProductFqn());
        dataProductVersionTag = command == null ? null : trimToNull(command.dataProductVersionTag());
        activityName = command == null ? null : trimToNull(command.activityName());
        taskName = command == null ? null : trimToNull(command.taskName());
        providerRunId = command == null ? null : trimToNull(command.providerRunId());
        requireNaturalKey();
        if (!StringUtils.hasText(taskName)) {
            throw new BadRequestException("Task name is required");
        }
        if (!StringUtils.hasText(providerRunId)) {
            throw new BadRequestException("Provider run id is required");
        }
    }

    private void requireNaturalKey() {
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

    private void requestPendingTask() {
        transactionalPort.doInTransaction(() -> {
            Activity activity = persistencePort.findOpenActivity(dataProductFqn, dataProductVersionTag, activityName);
            requireRunning(activity);
            Task task = resolveTask(activity);
            requireNoOtherTaskRunning(activity, task);
            requireNextPendingTask(activity, task);
            requireInstrumented(task);
            requirePending(task);
            task.setProviderRunId(providerRunId);
            Activity saved = persistencePort.save(activity);
            Task requested = resolveTask(saved);
            notificationPort.emitTaskExecutionRequested(saved, requested);
            presenter.presentTaskExecutionRequested(saved);
        });
    }

    private void requireRunning(Activity activity) {
        if (activity.getStatus() != ExecutionStatus.RUNNING) {
            throw new BadRequestException("Activity " + activity.getUuid() + " is not RUNNING");
        }
    }

    private void requireNoOtherTaskRunning(Activity activity, Task named) {
        List<String> runningNames = new ArrayList<>();
        for (Task task : tasksInSortOrder(activity)) {
            if (task.getStatus() == ExecutionStatus.RUNNING && !sameTask(task, named)) {
                runningNames.add(task.getName());
            }
        }
        if (runningNames.isEmpty()) {
            return;
        }
        if (runningNames.size() == 1) {
            throw new BadRequestException("Task " + taskName + " cannot be requested while task "
                    + runningNames.get(0) + " is RUNNING");
        }
        throw new BadRequestException("Task " + taskName + " cannot be requested while tasks "
                + String.join(", ", runningNames) + " are RUNNING");
    }

    private void requireNextPendingTask(Activity activity, Task named) {
        Task firstPending = null;
        for (Task task : tasksInSortOrder(activity)) {
            if (task.getStatus() == ExecutionStatus.PENDING) {
                firstPending = task;
                break;
            }
        }
        if (firstPending != null && !sameTask(firstPending, named)) {
            throw new BadRequestException("Task " + taskName + " is not the next task. Task "
                    + firstPending.getName() + " is still PENDING");
        }
    }

    private void requireInstrumented(Task task) {
        if (executorPort.findExecutionMode(task.getExecutorName()) != ExecutionMode.INSTRUMENTED) {
            throw new BadRequestException("Task " + taskName + " is not instrumented");
        }
    }

    private void requirePending(Task task) {
        if (task.getStatus() != ExecutionStatus.PENDING) {
            throw new BadRequestException("Task " + taskName + " is not PENDING");
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

    private List<Task> tasksInSortOrder(Activity activity) {
        List<Task> tasks = new ArrayList<>();
        if (activity.getTasks() != null) {
            tasks.addAll(activity.getTasks());
        }
        tasks.sort(Comparator.comparing(Task::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())));
        return tasks;
    }

    private static boolean sameTask(Task left, Task right) {
        return left == right || (left.getUuid() != null && left.getUuid().equals(right.getUuid()));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
