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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

/**
 * Full-control happy path. Brackets run only while the Policy service is
         * inactive.
 * 
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
 * 
 * This class is {@code ExecuteTask}. It sets a pending task to running.
 * For an instrumented task, that is the whole path: it returns after the task
 * is running, and it does not call the executor or Advance Activity.
 * The CLI then sends logs, results, and a terminal status. Status calls Advance Activity.
 * For a full-control task, it polls the executor until the run is terminal,
 * reads the logs once,
 * and calls {@code AdvanceActivity}.
 */
class ExecuteTask implements UseCase {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteTask.class);

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
        if (task == null) {
            return;
        }
        if (executorPort.findExecutionMode(task.getExecutorName()) != ExecutionMode.FULL_CONTROL) {
            // not full control, skip execution
            presenter.presentTaskExecuted(task);
            return;
        }
        // Replace ${activity.results.task.path} from results the CLI stored during earlier pipelines.
        // Only a succeeded task contributes. Stored values stay unchanged.
        Map<String, String> resolved = parametersPort.resolvePipelineParameters(task);
        if (!startRun(task, resolved)) {
            return;
        }
        // This pipeline uploads its own task results before the executor reports a terminal status.
        ExecutorRunStatus runStatus = followRunUntilItEnds(task);
        Optional<TaskLog> log = readRunLogOrEmpty(task);
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
            if (activity.getStatus() != ExecutionStatus.RUNNING) {
                return null;
            }
            Task task = findTask(activity, command.taskUuid());
            if (task.getStatus() != ExecutionStatus.PENDING) {
                return null;
            }
            task.setStatus(ExecutionStatus.RUNNING);
            task.setStartedAt(now());
            persistencePort.save(activity);
            task.getActivity().getUuid();
            task.getExecutorParameters();
            task.getPipelineParameters();
            return task;
        }, null);
    }

    private boolean startRun(Task task, Map<String, String> resolved) {
        String providerRunId;
        try {
            providerRunId = executorPort.startRun(task, resolved);
        } catch (RuntimeException startFailed) {
            logger.warn("Executor start failed for task {}", task.getUuid(), startFailed);
            failRunningTask(task);
            return false;
        }
        if (!StringUtils.hasText(providerRunId)) {
            failRunningTask(task);
            return false;
        }
        recordProviderRunId(task, providerRunId);
        return true;
    }

    private void failRunningTask(Task task) {
        Timestamp finishedAt = now();
        boolean failed = Boolean.TRUE.equals(transactionalPort.doInTransactionWithResults(ignored -> {
            Activity activity = persistencePort.findActivity(task.getActivity().getUuid());
            Task persisted = findTask(activity, task.getUuid());
            if (persisted.getStatus() != ExecutionStatus.RUNNING) {
                return false;
            }
            persisted.setStatus(ExecutionStatus.FAILED);
            persisted.setFinishedAt(finishedAt);
            persistencePort.save(activity);
            return true;
        }, null));
        if (failed) {
            task.setStatus(ExecutionStatus.FAILED);
            task.setFinishedAt(finishedAt);
        }
        presenter.presentTaskExecuted(task);
        advancePort.advanceActivity(task.getActivity().getUuid());
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
        int max = executorPort.maxStatusReads();
        for (int attempt = 1; attempt <= max; attempt++) {
            ExecutorRunStatus status;
            try {
                status = executorPort.readRunStatus(task);
            } catch (RuntimeException readFailed) {
                logger.warn("Executor status read failed for task {} on attempt {}", task.getUuid(), attempt, readFailed);
                if (attempt == max) {
                    return ExecutorRunStatus.FAILED;
                }
                executorPort.waitBeforeNextStatusRead();
                continue;
            }
            if (status != ExecutorRunStatus.RUNNING) {
                return status == null ? ExecutorRunStatus.FAILED : status;
            }
            if (attempt == max) {
                return ExecutorRunStatus.FAILED;
            }
            executorPort.waitBeforeNextStatusRead();
        }
        return ExecutorRunStatus.FAILED;
    }

    private Optional<TaskLog> readRunLogOrEmpty(Task task) {
        try {
            return executorPort.readRunLog(task);
        } catch (RuntimeException ignored) {
            logger.warn("Executor log read failed for task {}", task.getUuid());
            return Optional.empty();
        }
    }

    private void recordRunOutcome(Task task, ExecutorRunStatus runStatus, Optional<TaskLog> log) {
        Timestamp finishedAt = now();
        ExecutionStatus status = toExecutionStatus(runStatus);
        boolean recorded = Boolean.TRUE.equals(transactionalPort.doInTransactionWithResults(ignored -> {
            Activity activity = persistencePort.findActivity(task.getActivity().getUuid());
            Task persisted = findTask(activity, task.getUuid());
            if (persisted.getStatus() != ExecutionStatus.RUNNING) {
                return false;
            }
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
            return true;
        }, null));
        if (!recorded) {
            return;
        }
        if (log.isPresent()) {
            rememberLog(task, log.get());
        }
        task.setStatus(status);
        task.setFinishedAt(finishedAt);
    }

    private static ExecutionStatus toExecutionStatus(ExecutorRunStatus runStatus) {
        if (runStatus == ExecutorRunStatus.SUCCEEDED) {
            return ExecutionStatus.SUCCEEDED;
        }
        if (runStatus == ExecutorRunStatus.CANCELED) {
            return ExecutionStatus.CANCELED;
        }
        return ExecutionStatus.FAILED;
    }

    private void rememberLog(Task task, TaskLog entry) {
        if (task.getLogs() == null) {
            task.setLogs(new ArrayList<>());
        }
        if (!task.getLogs().contains(entry)) {
            task.getLogs().add(entry);
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
