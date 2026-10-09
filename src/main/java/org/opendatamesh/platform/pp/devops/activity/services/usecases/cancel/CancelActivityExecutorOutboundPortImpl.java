package org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel;

import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClient;
import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClientFactory;
import org.opendatamesh.platform.pp.devops.exceptions.InternalException;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientException;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CancelActivityExecutorOutboundPortImpl implements CancelActivityExecutorOutboundPort {

    private static final Logger logger = LoggerFactory.getLogger(CancelActivityExecutorOutboundPortImpl.class);
    private static final int MAX_CALLS = 3;

    private final ExecutorClientFactory executorClientFactory;
    private final ExecutorServicesProperties executorServicesProperties;

    CancelActivityExecutorOutboundPortImpl(ExecutorClientFactory executorClientFactory,
                                           ExecutorServicesProperties executorServicesProperties) {
        this.executorClientFactory = executorClientFactory;
        this.executorServicesProperties = executorServicesProperties;
    }

    @Override
    public ExecutionMode findExecutionMode(String executorName) {
        return executorServicesProperties.findExecutor(executorName)
                .map(executor -> executor.executionMode())
                .orElseThrow(() -> new InternalException("Executor " + executorName + " is not declared"));
    }

    @Override
    public CancelRunResult cancelRun(Task task) {
        ExecutorClient client = executorClientFactory.getExecutorClient(task.getExecutorName(), task.getActivity().getUuid());
        for (int attempt = 1; attempt <= MAX_CALLS; attempt++) {
            try {
                client.cancelTask(task.getProviderRunId());
                return CancelRunResult.POSTED;
            } catch (ClientException exception) {
                if (exception.getCode() >= 400 && exception.getCode() <= 499) {
                    return CancelRunResult.ALREADY_FINISHED;
                }
                logger.warn("Executor cancel failed for task {} on attempt {} with status {}",
                        task.getUuid(), attempt, exception.getCode());
            } catch (RuntimeException ignored) {
                logger.warn("Executor cancel failed for task {} on attempt {}", task.getUuid(), attempt);
            }
        }
        return CancelRunResult.UNREACHABLE;
    }
}
