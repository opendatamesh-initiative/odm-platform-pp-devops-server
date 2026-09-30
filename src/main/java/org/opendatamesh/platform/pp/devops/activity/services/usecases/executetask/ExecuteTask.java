package org.opendatamesh.platform.pp.devops.activity.services.usecases.executetask;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskLog;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
import org.opendatamesh.platform.pp.devops.executor.ExecutorRunStatus;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

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
 * This class is {@code ExecuteTask}. It polls the executor until the run is terminal, reads the logs once,
 * and calls {@code AdvanceActivity}.
 */
class ExecuteTask implements UseCase {

    private final ExecuteTaskCommand command;
    private final ExecuteTaskPresenter presenter;
    private final ExecuteTaskPersistenceOutboundPort persistencePort;
    private final ExecuteTaskPipelineParametersOutboundPort parametersPort;
    private final ExecuteTaskExecutorOutboundPort executorPort;
    private final ExecuteTaskAdvanceActivityOutboundPort advancePort;
    private final TransactionalOutboundPort transactionalPort;

    ExecuteTask(ExecuteTaskCommand command,
                ExecuteTaskPresenter presenter,
                ExecuteTaskPersistenceOutboundPort persistencePort,
                ExecuteTaskPipelineParametersOutboundPort parametersPort,
                ExecuteTaskExecutorOutboundPort executorPort,
                ExecuteTaskAdvanceActivityOutboundPort advancePort,
                TransactionalOutboundPort transactionalPort) {
        this.command = command;
        this.presenter = presenter;
        this.persistencePort = persistencePort;
        this.parametersPort = parametersPort;
        this.executorPort = executorPort;
        this.advancePort = advancePort;
        this.transactionalPort = transactionalPort;
    }

    @Override
    public void execute() {
        validateCommand();
        Task task = startTaskExecution();
        if (executorPort.findExecutionMode(task.getExecutorName()) != ExecutionMode.FULL_CONTROL) {
            presenter.presentTaskExecuted(task);
            return;
        }
        // Replace ${activity.results.task.path} from results the CLI stored during earlier pipelines.
        // Only a task's own pipeline, while it runs, writes that task's results, so they are already
        // stored once polling of that run returned a terminal status. Stored values stay unchanged.
        Map<String, String> resolved = parametersPort.resolvePipelineParameters(task);
        String providerRunId = executorPort.startRun(task, resolved);
        recordProviderRunId(task, providerRunId);
        // This pipeline uploads its own task results before the executor reports a terminal status.
        ExecutorRunStatus runStatus = followRunUntilItEnds(task);
        Optional<TaskLog> log = executorPort.readRunLog(task);
        recordRunOutcome(task, runStatus, log);
        presenter.presentTaskExecuted(task);
        advancePort.advanceActivity(task.getActivity().getUuid());
    }

    private void validateCommand() {
        if (command == null || !StringUtils.hasText(command.activityUuid())) {
            throw new BadRequestException("Activity UUID is required");
        }
        if (!StringUtils.hasText(command.taskUuid())) {
            throw new BadRequestException("Task UUID is required");
        }
    }

    private Task startTaskExecution() {
        return transactionalPort.doInTransactionWithResults(ignored -> {
            Activity activity = persistencePort.findActivity(command.activityUuid());
            requireRunning(activity);
            Task task = findTask(activity, command.taskUuid());
            requirePending(task);
            task.setStatus(ExecutionStatus.RUNNING);
            task.setStartedAt(now());
            persistencePort.save(activity);
            task.getActivity().getUuid();
            task.getExecutorParameters();
            task.getPipelineParameters();
            return task;
        }, null);
    }

    private void recordProviderRunId(Task task, String providerRunId) {
        transactionalPort.doInTransaction(() -> {
            Activity activity = persistencePort.findActivity(task.getActivity().getUuid());
            Task persisted = findTask(activity, task.getUuid());
            persisted.setProviderRunId(providerRunId);
            persistencePort.save(activity);
        });
        task.setProviderRunId(providerRunId);
    }

    private ExecutorRunStatus followRunUntilItEnds(Task task) {
        int attempt = 0;
        ExecutorRunStatus status = executorPort.readRunStatus(task);
        while (status == ExecutorRunStatus.RUNNING) {
            executorPort.waitBeforeNextStatusRead(++attempt);
            status = executorPort.readRunStatus(task);
        }
        return status;
    }

    private void recordRunOutcome(Task task, ExecutorRunStatus runStatus, Optional<TaskLog> log) {
        Timestamp finishedAt = now();
        ExecutionStatus status = runStatus == ExecutorRunStatus.SUCCEEDED
                ? ExecutionStatus.SUCCEEDED
                : ExecutionStatus.FAILED;
        transactionalPort.doInTransaction(() -> {
            Activity activity = persistencePort.findActivity(task.getActivity().getUuid());
            Task persisted = findTask(activity, task.getUuid());
            if (log.isPresent()) {
                TaskLog entry = log.get();
                entry.setTask(persisted);
                if (persisted.getLogs() == null) {
                    persisted.setLogs(new ArrayList<>());
                }
                persisted.getLogs().add(entry);
            }
            persisted.setStatus(status);
            persisted.setFinishedAt(finishedAt);
            persistencePort.save(activity);
        });
        if (log.isPresent()) {
            rememberLog(task, log.get());
        }
        task.setStatus(status);
        task.setFinishedAt(finishedAt);
    }

    private void rememberLog(Task task, TaskLog entry) {
        if (task.getLogs() == null) {
            task.setLogs(new ArrayList<>());
        }
        if (!task.getLogs().contains(entry)) {
            task.getLogs().add(entry);
        }
    }

    private void requireRunning(Activity activity) {
        if (activity.getStatus() != ExecutionStatus.RUNNING) {
            throw new BadRequestException("Activity " + activity.getUuid() + " is not RUNNING");
        }
    }

    private void requirePending(Task task) {
        if (task.getStatus() != ExecutionStatus.PENDING) {
            throw new BadRequestException("Task " + task.getUuid() + " can be executed only if PENDING");
        }
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

    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }
}
