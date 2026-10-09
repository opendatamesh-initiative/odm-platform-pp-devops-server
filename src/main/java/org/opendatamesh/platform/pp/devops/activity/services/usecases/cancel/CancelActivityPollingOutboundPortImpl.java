package org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel;

import org.opendatamesh.platform.pp.devops.exceptions.InternalException;
import org.opendatamesh.platform.pp.devops.executor.ExecutorPollingProperties;
import org.opendatamesh.platform.pp.devops.executor.ExecutorSecretsProperties;

import java.time.Duration;

class CancelActivityPollingOutboundPortImpl implements CancelActivityPollingOutboundPort {

    private static final Duration ONE_SECOND = Duration.ofSeconds(1);

    private final ExecutorPollingProperties pollingProperties;
    private final ExecutorSecretsProperties secretsProperties;

    CancelActivityPollingOutboundPortImpl(ExecutorPollingProperties pollingProperties,
                                          ExecutorSecretsProperties secretsProperties) {
        this.pollingProperties = pollingProperties;
        this.secretsProperties = secretsProperties;
    }

    @Override
    public int maxStatusReads() {
        return pollingProperties.maxStatusReads(secretsProperties.getTtl());
    }

    @Override
    public void waitOnePollInterval() {
        sleep(pollingProperties.getInterval().toMillis());
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
