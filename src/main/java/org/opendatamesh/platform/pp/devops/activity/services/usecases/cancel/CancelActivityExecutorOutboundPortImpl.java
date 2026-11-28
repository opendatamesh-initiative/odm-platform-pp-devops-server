package org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel;

import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClient;
import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClientFactory;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskCancelCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStatusCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStatusRes;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientException;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;
import org.opendatamesh.platform.pp.devops.executor.RunFollowRegistry;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CancelActivityExecutorOutboundPortImpl implements CancelActivityExecutorOutboundPort {

    private static final Logger logger = LoggerFactory.getLogger(CancelActivityExecutorOutboundPortImpl.class);
    private static final int MAX_CALLS = 3;

    private final ExecutorClientFactory executorClientFactory;
    private final ExecutorServicesProperties executorServicesProperties;
    private final TaskMapper taskMapper;
    private final RunFollowRegistry runFollowRegistry;

    CancelActivityExecutorOutboundPortImpl(ExecutorClientFactory executorClientFactory,
                                           ExecutorServicesProperties executorServicesProperties,
                                           TaskMapper taskMapper,
                                           RunFollowRegistry runFollowRegistry) {
        this.executorClientFactory = executorClientFactory;
        this.executorServicesProperties = executorServicesProperties;
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
    public boolean isRunFollowed(String taskUuid) {
        return runFollowRegistry.isFollowed(taskUuid);
    }

    @Override
    public CancelRunResult cancelRun(Task task) {
        ExecutorClient client = executorClientFactory.getExecutorClient(task.getExecutorName(), task.getActivity().getUuid());
        for (int attempt = 1; attempt <= MAX_CALLS; attempt++) {
            try {
                client.cancelTask(cancelCommand(task));
                return CancelRunResult.POSTED;
            } catch (ClientException exception) {
                if (exception.getCode() == 409) {
                    return CancelRunResult.ALREADY_FINISHED;
                }
                if (exception.getCode() == 404) {
                    // A 404 can mean "no such run" or "no such route": ask the run status to tell them apart.
                    return runEndedOrUnreachable(client, task);
                }
                logger.warn("Executor cancel failed for task {} on attempt {} with status {}",
                        task.getUuid(), attempt, exception.getCode());
            } catch (RuntimeException ignored) {
                logger.warn("Executor cancel failed for task {} on attempt {}", task.getUuid(), attempt);
            }
        }
        return CancelRunResult.UNREACHABLE;
    }

    /**
     * After a 404 on cancel: the run has ended (or the executor does not know it) only when its status says so.
     * A run the executor still reports as RUNNING was not stopped, so the cancel failed.
     */
    private CancelRunResult runEndedOrUnreachable(ExecutorClient client, Task task) {
        try {
            ExecutorTaskStatusRes status = client.getTaskStatus(new ExecutorTaskStatusCommandRes(
                    taskMapper.jsonNodeToExecutorParameters(task.getExecutorParameters()),
                    task.getProviderRunId()
            ));
            String value = status == null || status.getStatus() == null ? "" : status.getStatus().trim();
            if ("RUNNING".equalsIgnoreCase(value) || value.isEmpty()) {
                logger.warn("Executor answered 404 to cancel task {} but reports its run as '{}'", task.getUuid(), value);
                return CancelRunResult.UNREACHABLE;
            }
            return CancelRunResult.ALREADY_FINISHED;
        } catch (ClientException statusFailed) {
            if (statusFailed.getCode() == 404) {
                return CancelRunResult.ALREADY_FINISHED;
            }
            logger.warn("Executor answered 404 to cancel task {} and its status could not be read (HTTP {})",
                    task.getUuid(), statusFailed.getCode());
            return CancelRunResult.UNREACHABLE;
        } catch (RuntimeException statusFailed) {
            logger.warn("Executor answered 404 to cancel task {} and its status could not be read", task.getUuid());
            return CancelRunResult.UNREACHABLE;
        }
    }

    private ExecutorTaskCancelCommandRes cancelCommand(Task task) {
        return new ExecutorTaskCancelCommandRes(
                taskMapper.jsonNodeToExecutorParameters(task.getExecutorParameters()),
                task.getProviderRunId()
        );
    }
}
