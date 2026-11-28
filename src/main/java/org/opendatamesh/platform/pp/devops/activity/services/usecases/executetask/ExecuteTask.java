package org.opendatamesh.platform.pp.devops.activity.services.usecases.executetask;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskLog;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.devops.utils.entities.StoredTextLimits;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
import org.opendatamesh.platform.pp.devops.executor.ExecutorRunStatus;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
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
        StartedTask started = transactionalPort.doInTransactionWithResults(ignored -> startTaskExecution(), null);
        if (started == null) {
            return;
        }
        Task task = started.task();
        if (started.ended()) {
            presenter.presentTaskExecuted(task);
            advancePort.advanceActivity(task.getActivity().getUuid());
            return;
        }
        if (started.mode() != ExecutionMode.FULL_CONTROL) {
            // Instrumented: the CLI reports logs, results and the final status.
            presenter.presentTaskExecuted(task);
            return;
        }
        // Cancel reads this to know that the outcome of this run will be recorded here.
        executorPort.followStarted(task.getUuid());
        try {
            if (startRun(task)) {
                // This pipeline uploads its own task results before the executor reports a terminal status.
                RunOutcome outcome = followRunUntilItEnds(task);
                List<TaskLog> logs = new ArrayList<>();
                readRunLogOrEmpty(task).ifPresent(logs::add);
                // A failure DevOps decided (unreadable status, poll limit) gets its reason in the task logs.
                outcome.failureReason().ifPresent(reason -> logs.add(failureLog(reason)));
                recordRunOutcome(task, outcome.status(), logs);
            }
        } finally {
            executorPort.followEnded(task.getUuid());
        }
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

    /**
     * Sets the pending task to RUNNING. A task whose executor is no longer declared cannot run:
     * it is set to FAILED at once, with the reason in its logs, so the activity can close.
     */
    private StartedTask startTaskExecution() {
        Activity activity = persistencePort.findForUpdate(command.activityUuid());
        if (activity.getStatus() != ExecutionStatus.RUNNING) {
            return null;
        }
        Task task = findTask(activity, command.taskUuid());
        if (task.getStatus() == ExecutionStatus.SUCCEEDED || task.getStatus() == ExecutionStatus.FAILED
                || task.getStatus() == ExecutionStatus.CANCELED) {
            // A repeated approval of a task that has ended: advance again, in case the first advance failed.
            task.getActivity().getUuid();
            return new StartedTask(task, null, true);
        }
        if (task.getStatus() != ExecutionStatus.PENDING) {
            return null;
        }
        ExecutionMode mode = executorPort.findExecutionMode(task.getExecutorName());
        Timestamp now = now();
        task.setStartedAt(now);
        if (mode == null) {
            TaskLog reason = failureLog("The task is marked FAILED because executor " + task.getExecutorName()
                    + " is no longer declared in the DevOps configuration.");
            reason.setTask(task);
            if (task.getLogs() == null) {
                task.setLogs(new ArrayList<>());
            }
            task.getLogs().add(reason);
            task.setStatus(ExecutionStatus.FAILED);
            task.setFinishedAt(now);
        } else {
            task.setStatus(ExecutionStatus.RUNNING);
        }
        persistencePort.save(activity);
        task.getActivity().getUuid();
        task.getExecutorParameters();
        task.getPipelineParameters();
        task.getLogs().size();
        return new StartedTask(task, mode, mode == null);
    }

    /**
     * Starts the run on the executor and stores its provider run id.
     * Returns false when the task could not start; its failure is then recorded.
     */
    private boolean startRun(Task task) {
        String providerRunId;
        try {
            // Replace ${activity.results.task.path} from results the CLI stored during earlier pipelines.
            // Only a succeeded task contributes. Stored values stay unchanged.
            Map<String, String> resolved = parametersPort.resolvePipelineParameters(task);
            providerRunId = executorPort.startRun(task, resolved);
        } catch (RuntimeException startFailed) {
            String reason = executorPort.describeFailure(task, ExecuteTaskExecutorOutboundPort.ExecutorCall.START, startFailed);
            logger.warn("Executor start failed for task {} of activity {}: {}",
                    task.getUuid(), task.getActivity().getUuid(), reason, startFailed);
            recordRunOutcome(task, ExecutorRunStatus.FAILED, List.of(failureLog(reason)));
            return false;
        }
        if (!StringUtils.hasText(providerRunId)) {
            String reason = "Executor " + task.getExecutorName() + " returned no provider run id when starting the task";
            logger.warn("{} (task {})", reason, task.getUuid());
            recordRunOutcome(task, ExecutorRunStatus.FAILED, List.of(failureLog(reason)));
            return false;
        }
        if (providerRunId.length() > StoredTextLimits.PROVIDER_RUN_ID_MAX_CHARS) {
            String reason = "The task is marked FAILED because executor " + task.getExecutorName()
                    + " returned a provider run id longer than " + StoredTextLimits.PROVIDER_RUN_ID_MAX_CHARS
                    + " characters, which DevOps cannot store. The run may have started in the tool and is not tracked: "
                    + excerpt(providerRunId);
            logger.warn("{} (task {})", reason, task.getUuid());
            recordRunOutcome(task, ExecutorRunStatus.FAILED, List.of(failureLog(reason)));
            return false;
        }
        logger.info("Executor {} started task {} of activity {} with provider run id {}",
                task.getExecutorName(), task.getUuid(), task.getActivity().getUuid(), providerRunId);
        try {
            recordProviderRunId(task, providerRunId);
        } catch (RuntimeException storeFailed) {
            String reason = "The task is marked FAILED because DevOps could not store provider run id " + providerRunId
                    + " of executor " + task.getExecutorName() + ". The run may still be going on in the tool and is not tracked.";
            logger.error("{} (task {})", reason, task.getUuid(), storeFailed);
            recordRunOutcome(task, ExecutorRunStatus.FAILED, List.of(failureLog(reason)));
            return false;
        }
        return true;
    }

    private void recordProviderRunId(Task task, String providerRunId) {
        transactionalPort.doInTransaction(() -> {
            Activity activity = persistencePort.findForUpdate(task.getActivity().getUuid());
            Task persisted = findTask(activity, task.getUuid());
            persisted.setProviderRunId(providerRunId);
            persistencePort.save(activity);
        });
        task.setProviderRunId(providerRunId);
    }

    private RunOutcome followRunUntilItEnds(Task task) {
        int max = executorPort.maxStatusReads();
        String lastReadFailure = null;
        for (int attempt = 1; attempt <= max; attempt++) {
            ExecuteTaskExecutorOutboundPort.StatusRead read;
            try {
                read = executorPort.readRunStatus(task);
            } catch (RuntimeException readFailed) {
                lastReadFailure = executorPort.describeFailure(task, ExecuteTaskExecutorOutboundPort.ExecutorCall.READ_STATUS, readFailed);
                logger.warn("Executor status read failed for task {} (run {}) on attempt {} of {}: {}",
                        task.getUuid(), task.getProviderRunId(), attempt, max, lastReadFailure, readFailed);
                if (attempt == max) {
                    return RunOutcome.failed("The task is marked FAILED because the run status could not be read after "
                            + max + " attempts. Last error: " + lastReadFailure);
                }
                executorPort.waitBeforeNextStatusRead();
                continue;
            }
            if (read == null || read.status() == null) {
                return RunOutcome.failed("The task is marked FAILED because executor " + task.getExecutorName()
                        + " returned no run status.");
            }
            if (read.failureReason() != null) {
                return RunOutcome.failed(read.failureReason());
            }
            ExecutorRunStatus status = read.status();
            if (status != ExecutorRunStatus.RUNNING) {
                logger.info("Executor {} reported run {} of task {} as {}",
                        task.getExecutorName(), task.getProviderRunId(), task.getUuid(), status);
                return RunOutcome.of(status);
            }
            if (attempt == max) {
                logger.warn("Run {} of task {} still RUNNING after {} status reads: marking the task FAILED",
                        task.getProviderRunId(), task.getUuid(), max);
                return RunOutcome.failed("The task is marked FAILED because the run was still RUNNING after " + max
                        + " status reads (the polling limit of the executor services). The run may still be going on in "
                        + task.getExecutorName() + " under provider run id " + task.getProviderRunId() + ".");
            }
            executorPort.waitBeforeNextStatusRead();
        }
        return RunOutcome.failed("The task is marked FAILED because no status read was allowed (polling limit is "
                + max + ")" + (lastReadFailure == null ? "" : ". Last error: " + lastReadFailure));
    }

    private Optional<TaskLog> readRunLogOrEmpty(Task task) {
        try {
            return executorPort.readRunLog(task);
        } catch (RuntimeException readFailed) {
            logger.warn("Executor log read failed for task {} (run {}): {}", task.getUuid(), task.getProviderRunId(),
                    executorPort.describeFailure(task, ExecuteTaskExecutorOutboundPort.ExecutorCall.READ_LOGS, readFailed), readFailed);
            return Optional.empty();
        }
    }

    /**
     * Stores the terminal status of the run with its logs. When the logs cannot be stored, the status is
     * stored anyway with a short reason, so a run never stays RUNNING because of what it printed.
     */
    private void recordRunOutcome(Task task, ExecutorRunStatus runStatus, List<TaskLog> logs) {
        try {
            storeRunOutcome(task, runStatus, logs);
        } catch (RuntimeException storeFailed) {
            logger.error("Could not store the outcome of task {} with its logs; storing it without them",
                    task.getUuid(), storeFailed);
            storeRunOutcome(task, runStatus, List.of(failureLog("DevOps could not store the logs of this run: "
                    + excerpt(storeFailed.getMessage()))));
        }
    }

    private void storeRunOutcome(Task task, ExecutorRunStatus runStatus, List<TaskLog> logs) {
        Timestamp finishedAt = now();
        ExecutionStatus status = toExecutionStatus(runStatus);
        boolean recorded = Boolean.TRUE.equals(transactionalPort.doInTransactionWithResults(ignored -> {
            Activity activity = persistencePort.findForUpdate(task.getActivity().getUuid());
            Task persisted = findTask(activity, task.getUuid());
            if (persisted.getStatus() != ExecutionStatus.RUNNING) {
                return false;
            }
            if (persisted.getLogs() == null) {
                persisted.setLogs(new ArrayList<>());
            }
            for (TaskLog entry : logs) {
                entry.setUuid(null);
                entry.setTask(persisted);
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
        for (TaskLog entry : logs) {
            rememberLog(task, entry);
        }
        task.setStatus(status);
        task.setFinishedAt(finishedAt);
    }

    private static String excerpt(String text) {
        if (text == null) {
            return "";
        }
        String clean = StoredTextLimits.withoutNulCharacters(text).strip();
        return clean.length() > 1000 ? clean.substring(0, 1000) + "…" : clean;
    }

    private static TaskLog failureLog(String reason) {
        TaskLog log = new TaskLog();
        log.setContent(StoredTextLimits.fitLogContent(reason));
        log.setGeneratedAt(now());
        return log;
    }

    /** A task set to RUNNING with its executor's mode, or a task that has ended (now, or before) and only needs an advance. */
    private record StartedTask(Task task, ExecutionMode mode, boolean ended) {
    }

    /** The terminal status of a run, with the reason when DevOps itself decided the failure. */
    private record RunOutcome(ExecutorRunStatus status, Optional<String> failureReason) {

        static RunOutcome of(ExecutorRunStatus status) {
            return new RunOutcome(status, Optional.empty());
        }

        static RunOutcome failed(String reason) {
            return new RunOutcome(ExecutorRunStatus.FAILED, Optional.of(reason));
        }
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
