package org.opendatamesh.platform.pp.devops.activity.services.usecases.executetask;

import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskLog;
import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClient;
import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClientFactory;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskLogsRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskLogsCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStatusCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartResultRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStatusRes;
import org.opendatamesh.platform.pp.devops.exceptions.InternalException;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientAnswerBrokenException;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientException;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientRequestFailedException;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientTransportException;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientUnreadableResponseException;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
import org.opendatamesh.platform.pp.devops.executor.ExecutorRunStatus;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;
import org.opendatamesh.platform.pp.devops.executor.RunFollowRegistry;
import org.opendatamesh.platform.pp.devops.utils.entities.StoredTextLimits;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

class ExecuteTaskExecutorOutboundPortImpl implements ExecuteTaskExecutorOutboundPort {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteTaskExecutorOutboundPortImpl.class);

    /** Bounds an executor error body copied into the task logs. */
    private static final int MAX_FAILURE_CHARS = 4000;

    private final ExecutorServicesProperties executorServicesProperties;
    private final ExecutorClientFactory executorClientFactory;
    private final TaskMapper taskMapper;
    private final RunFollowRegistry runFollowRegistry;

    ExecuteTaskExecutorOutboundPortImpl(ExecutorServicesProperties executorServicesProperties,
                                        ExecutorClientFactory executorClientFactory,
                                        TaskMapper taskMapper,
                                        RunFollowRegistry runFollowRegistry) {
        this.executorServicesProperties = executorServicesProperties;
        this.executorClientFactory = executorClientFactory;
        this.taskMapper = taskMapper;
        this.runFollowRegistry = runFollowRegistry;
    }

    @Override
    public ExecutionMode findExecutionMode(String executorName) {
        return executorServicesProperties.findExecutor(executorName)
                .map(executor -> executor.executionMode())
                .orElse(null);
    }

    @Override
    public void followStarted(String taskUuid) {
        runFollowRegistry.followStarted(taskUuid);
    }

    @Override
    public void followEnded(String taskUuid) {
        runFollowRegistry.followEnded(taskUuid);
    }

    @Override
    public String startRun(Task task, Map<String, String> resolvedPipelineParameters) {
        ExecutorClient client = client(task);
        ExecutorTaskStartCommandRes command = new ExecutorTaskStartCommandRes(
                taskMapper.jsonNodeToExecutorParameters(task.getExecutorParameters()),
                resolvedPipelineParameters
        );
        ExecutorTaskStartResultRes result = client.startTask(command);
        if (result == null || !StringUtils.hasText(result.getProviderRunId())) {
            throw new InternalException("Executor " + task.getExecutorName() + " returned no provider run id");
        }
        return result.getProviderRunId().trim();
    }

    @Override
    public StatusRead readRunStatus(Task task) {
        ExecutorTaskStatusRes status = client(task).getTaskStatus(statusCommand(task));
        if (status == null || !StringUtils.hasText(status.getStatus())) {
            logger.warn("Executor {} returned no status for task {} (run {})", task.getExecutorName(), task.getUuid(),
                    task.getProviderRunId());
            return StatusRead.unusable("The task is marked FAILED because executor " + task.getExecutorName()
                    + " returned no status for run " + task.getProviderRunId() + ".");
        }
        return toRunStatus(status.getStatus(), task);
    }

    @Override
    public void waitBeforeNextStatusRead() {
        sleep(executorServicesProperties.getPollingInterval().toMillis());
    }

    @Override
    public int maxStatusReads() {
        return executorServicesProperties.maxStatusReads(executorServicesProperties.getTtl());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new InternalException("Interrupted while waiting to read executor status");
        }
    }

    @Override
    public Optional<TaskLog> readRunLog(Task task) {
        ExecutorTaskLogsRes logs = client(task).getTaskLogs(logsCommand(task));
        if (logs == null || !StringUtils.hasText(logs.getContent())) {
            return Optional.empty();
        }
        TaskLog log = new TaskLog();
        // A pipeline log can be far longer than a stored log: keep its end, so the outcome is always recorded.
        log.setContent(StoredTextLimits.fitLogContent(logs.getContent()));
        if (logs.getGeneratedAt() == null) {
            log.setGeneratedAt(new Timestamp(System.currentTimeMillis()));
        } else {
            log.setGeneratedAt(new Timestamp(logs.getGeneratedAt().getTime()));
        }
        return Optional.of(log);
    }

    @Override
    public String describeFailure(Task task, ExecutorCall call, RuntimeException failure) {
        String action = call.action();
        String executor = "Executor " + task.getExecutorName();
        if (waitedForAFreeConnection(failure)) {
            return "DevOps had no free connection to executor " + task.getExecutorName() + " to " + action
                    + " in time: the request was not sent, the executor was not contacted.";
        }
        if (failure instanceof ClientTransportException transportFailure) {
            String message = excerpt(transportFailure.getResponseBody());
            String detail = message.isEmpty() ? "" : " (" + message + ")";
            if (transportFailure instanceof ClientAnswerBrokenException || requestWasSent(transportFailure)) {
                String sentence = transportFailure instanceof ClientAnswerBrokenException
                        ? "The answer of executor " + task.getExecutorName() + " broke off when asked to " + action + detail + "."
                        : executor + " did not answer when asked to " + action + detail + ".";
                return call == ExecutorCall.START ? sentence + " The request reached the executor, which may have"
                        + " started the run: a run started this way is not tracked by DevOps." : sentence;
            }
            return "Could not reach executor " + task.getExecutorName() + " to " + action + detail + ".";
        }
        if (failure instanceof ClientUnreadableResponseException unreadable) {
            String message = excerpt(unreadable.getResponseBody());
            return executor + " returned an answer that could not be read when asked to " + action
                    + (message.isEmpty() ? "" : " (" + message + ")");
        }
        if (failure instanceof ClientRequestFailedException requestFailed) {
            String message = excerpt(requestFailed.getResponseBody());
            return "DevOps could not send the request to executor " + task.getExecutorName() + " to " + action
                    + (message.isEmpty() ? "" : ": " + message);
        }
        if (failure instanceof ClientException clientFailure) {
            String body = excerpt(clientFailure.getResponseBody());
            return executor + " failed to " + action + " (HTTP " + clientFailure.getCode() + ")"
                    + (body.isEmpty() ? "" : ": " + body);
        }
        String message = excerpt(failure.getMessage());
        return executor + " failed to " + action + ": " + (message.isEmpty() ? failure.getClass().getSimpleName() : message);
    }

    /**
     * The connection was made and the request sent, but no answer came back: a read timeout, the connection closed
     * before an answer (Apache HttpClient's NoHttpResponseException), or lost (reset, broken pipe). Connect
     * timeouts, refusals, unknown hosts and unreachable networks are not.
     */
    private static boolean requestWasSent(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.http.HttpConnectTimeoutException
                    || cause instanceof org.apache.hc.client5.http.ConnectTimeoutException
                    || cause instanceof java.net.ConnectException
                    || cause instanceof java.net.NoRouteToHostException
                    || cause instanceof java.net.PortUnreachableException
                    || cause instanceof java.net.UnknownHostException) {
                return false;
            }
            if (cause instanceof java.net.http.HttpTimeoutException
                    || cause instanceof org.apache.hc.core5.http.NoHttpResponseException) {
                return true;
            }
            if (cause instanceof java.net.SocketTimeoutException) {
                String message = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase(Locale.ROOT);
                return !message.contains("connect");
            }
            if (cause instanceof java.net.SocketException) {
                // Connection reset or broken pipe on an established connection.
                return true;
            }
        }
        return false;
    }

    /** DevOps's own connection pool had no free connection: the request never left DevOps. */
    private static boolean waitedForAFreeConnection(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.apache.hc.core5.http.ConnectionRequestTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static String excerpt(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.strip();
        return trimmed.length() > MAX_FAILURE_CHARS ? trimmed.substring(0, MAX_FAILURE_CHARS) + "…" : trimmed;
    }

    private StatusRead toRunStatus(String status, Task task) {
        try {
            return StatusRead.of(ExecutorRunStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            logger.warn("Executor status {} is not a known run status for task {}", status, task.getUuid());
            return StatusRead.unusable("The task is marked FAILED because executor " + task.getExecutorName()
                    + " returned the unknown status '" + excerpt(status) + "' for run " + task.getProviderRunId()
                    + " (expected RUNNING, SUCCEEDED, FAILED or CANCELED).");
        }
    }

    private ExecutorClient client(Task task) {
        return executorClientFactory.getExecutorClient(task.getExecutorName(), task.getActivity().getUuid());
    }

    private ExecutorTaskStatusCommandRes statusCommand(Task task) {
        return new ExecutorTaskStatusCommandRes(
                taskMapper.jsonNodeToExecutorParameters(task.getExecutorParameters()),
                task.getProviderRunId()
        );
    }

    private ExecutorTaskLogsCommandRes logsCommand(Task task) {
        return new ExecutorTaskLogsCommandRes(
                taskMapper.jsonNodeToExecutorParameters(task.getExecutorParameters()),
                task.getProviderRunId()
        );
    }
}
