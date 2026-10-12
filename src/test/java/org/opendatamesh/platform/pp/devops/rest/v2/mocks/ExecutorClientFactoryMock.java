package org.opendatamesh.platform.pp.devops.rest.v2.mocks;

import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClient;
import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClientFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Primary executor factory for integration tests.
 * Every executor name returns the same scripted client. {@link #reset()} clears that script.
 */
@Component
@Primary
public class ExecutorClientFactoryMock extends IntegrationMock implements ExecutorClientFactory {

    private ScriptedExecutor executor;

    @Override
    public void reset() {
        executor = new ScriptedExecutor();
    }

    @Override
    public ExecutorClient getExecutorClient(String executorName, String activityUuid) {
        executor.opened(executorName);
        return executor;
    }

    public ScriptedExecutor executor() {
        return executor;
    }
}
