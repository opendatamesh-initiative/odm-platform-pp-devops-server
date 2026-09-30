package org.opendatamesh.platform.pp.devops.executor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "odm.utility-plane.executor-polling")
public class ExecutorPollingProperties {

    private Duration initialDelay = Duration.ofMillis(500);
    private Duration maxDelay = Duration.ofSeconds(60);

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(Duration initialDelay) {
        this.initialDelay = initialDelay;
    }

    public Duration getMaxDelay() {
        return maxDelay;
    }

    public void setMaxDelay(Duration maxDelay) {
        this.maxDelay = maxDelay;
    }

    public Duration delayForAttempt(int attempt) {
        int doublings = Math.max(0, attempt - 1);
        Duration delay = initialDelay == null ? Duration.ZERO : initialDelay;
        Duration ceiling = maxDelay == null ? delay : maxDelay;
        for (int i = 0; i < doublings; i++) {
            if (delay.compareTo(ceiling) >= 0) {
                return ceiling;
            }
            try {
                delay = delay.multipliedBy(2);
            } catch (ArithmeticException overflow) {
                return ceiling;
            }
        }
        return delay.compareTo(ceiling) > 0 ? ceiling : delay;
    }
}
