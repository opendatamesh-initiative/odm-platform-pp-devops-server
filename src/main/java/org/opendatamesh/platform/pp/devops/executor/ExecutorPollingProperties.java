package org.opendatamesh.platform.pp.devops.executor;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "odm.utility-plane.executor-polling")
public class ExecutorPollingProperties {

    static final String PROPERTY = "odm.utility-plane.executor-polling.interval";
    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(30);
    private static final Duration DEFAULT_SECRETS_TTL = Duration.ofHours(1);

    private Duration interval = DEFAULT_INTERVAL;

    @PostConstruct
    public void validate() {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalStateException(PROPERTY + " must be a positive duration");
        }
    }

    public Duration getInterval() {
        return interval;
    }

    public void setInterval(Duration interval) {
        this.interval = interval;
    }

    public int maxStatusReads(Duration secretsTtl) {
        Duration ttl = secretsTtl == null ? DEFAULT_SECRETS_TTL : secretsTtl;
        Duration every = interval == null ? DEFAULT_INTERVAL : interval;
        long intervalMillis = every.toMillis();
        if (intervalMillis <= 0) {
            return 1;
        }
        long reads = ttl.toMillis() / intervalMillis;
        if (reads < 1) {
            return 1;
        }
        return reads > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) reads;
    }
}
