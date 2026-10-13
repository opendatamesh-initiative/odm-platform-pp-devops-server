package org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel;

import org.opendatamesh.platform.pp.devops.exceptions.InternalException;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;

import java.time.Duration;

class CancelActivityPollingOutboundPortImpl implements CancelActivityPollingOutboundPort {

    private static final Duration ONE_SECOND = Duration.ofSeconds(1);

    private final ExecutorServicesProperties executorServicesProperties;

    CancelActivityPollingOutboundPortImpl(ExecutorServicesProperties executorServicesProperties) {
        this.executorServicesProperties = executorServicesProperties;
    }

    @Override
    public int maxStatusReads() {
        return executorServicesProperties.maxStatusReads(executorServicesProperties.getTtl());
    }

    @Override
    public void waitOnePollInterval() {
        sleep(executorServicesProperties.getPollingInterval().toMillis());
    }

    @Override
    public void waitOneSecond() {
        sleep(ONE_SECOND.toMillis());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new InternalException("Interrupted while waiting to read executor status");
        }
    }
}
