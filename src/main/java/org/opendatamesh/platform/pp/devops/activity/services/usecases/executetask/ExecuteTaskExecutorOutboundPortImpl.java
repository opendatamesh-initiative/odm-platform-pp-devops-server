package org.opendatamesh.platform.pp.devops.activity.services.usecases.executetask;

import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskLog;
import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClient;
import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClientFactory;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskLogsRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartResultRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStatusRes;
import org.opendatamesh.platform.pp.devops.exceptions.InternalException;
import org.opendatamesh.platform.pp.devops.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
import org.opendatamesh.platform.pp.devops.executor.ExecutorPollingProperties;
import org.opendatamesh.platform.pp.devops.executor.ExecutorRunStatus;
import org.opendatamesh.platform.pp.devops.executor.ExecutorSecretsProperties;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;
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

    private final ExecutorServicesProperties executorServicesProperties;
    private final ExecutorClientFactory executorClientFactory;
    private final ExecutorPollingProperties pollingProperties;
    private final ExecutorSecretsProperties secretsProperties;
    private final TaskMapper taskMapper;

    ExecuteTaskExecutorOutboundPortImpl(ExecutorServicesProperties executorServicesProperties,
                                        ExecutorClientFactory executorClientFactory,
                                        ExecutorPollingProperties pollingProperties,
                                        ExecutorSecretsProperties secretsProperties,
                                        TaskMapper taskMapper) {
        this.executorServicesProperties = executorServicesProperties;
        this.executorClientFactory = executorClientFactory;
        this.pollingProperties = pollingProperties;
        this.secretsProperties = secretsProperties;
        this.taskMapper = taskMapper;
    }

    @Override
    public ExecutionMode findExecutionMode(String executorName) {
        return executorServicesProperties.findExecutor(executorName)
                .map(executor -> executor.executionMode())
                .orElseThrow(() -> new NotFoundException("Executor " + executorName + " is not declared"));
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
        return result.getProviderRunId();
    }

    @Override
    public ExecutorRunStatus readRunStatus(Task task) {
        ExecutorTaskStatusRes status = client(task).getTaskStatus(task.getProviderRunId());
        if (status == null || !StringUtils.hasText(status.getStatus())) {
            logger.warn("Executor status is missing for task {}", task.getUuid());
            return ExecutorRunStatus.FAILED;
        }
        return toRunStatus(status.getStatus(), task);
    }

    @Override
    public void waitBeforeNextStatusRead() {
        sleep(pollingProperties.getInterval().toMillis());
    }

    @Override
    public int maxStatusReads() {
        return pollingProperties.maxStatusReads(secretsProperties.getTtl());
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
        ExecutorTaskLogsRes logs = client(task).getTaskLogs(task.getProviderRunId());
        if (logs == null || !StringUtils.hasText(logs.getContent())) {
            return Optional.empty();
        }
        TaskLog log = new TaskLog();
        log.setContent(logs.getContent());
        if (logs.getGeneratedAt() == null) {
            log.setGeneratedAt(new Timestamp(System.currentTimeMillis()));
        } else {
            log.setGeneratedAt(new Timestamp(logs.getGeneratedAt().getTime()));
        }
        return Optional.of(log);
    }

    private ExecutorRunStatus toRunStatus(String status, Task task) {
        try {
            return ExecutorRunStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            logger.warn("Executor status {} is not a known run status for task {}", status, task.getUuid());
            return ExecutorRunStatus.FAILED;
        }
    }

    private ExecutorClient client(Task task) {
        return executorClientFactory.getExecutorClient(task.getExecutorName(), task.getActivity().getUuid());
    }
}
