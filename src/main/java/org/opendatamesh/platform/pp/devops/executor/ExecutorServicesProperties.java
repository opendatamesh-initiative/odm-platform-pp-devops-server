package org.opendatamesh.platform.pp.devops.executor;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@ConfigurationProperties(prefix = "odm.utility-plane.executors")
public class ExecutorServicesProperties {

    static final String POLLING_INTERVAL_PROPERTY = "odm.utility-plane.executors.polling-interval";
    static final String SECRETS_TTL_PROPERTY = "odm.utility-plane.executors.secrets-ttl";

    private static final Duration DEFAULT_POLLING_INTERVAL = Duration.ofSeconds(30);
    private static final Duration DEFAULT_SECRETS_TTL = Duration.ofHours(1);

    private Duration pollingInterval = DEFAULT_POLLING_INTERVAL;
    private Duration secretsTtl = DEFAULT_SECRETS_TTL;
    private Map<String, ExecutorServiceProperties> services = new LinkedHashMap<>();

    @PostConstruct
    public void validate() {
        if (pollingInterval == null || pollingInterval.isZero() || pollingInterval.isNegative()) {
            throw new IllegalStateException(POLLING_INTERVAL_PROPERTY + " must be a positive duration");
        }
        if (secretsTtl == null || secretsTtl.isZero() || secretsTtl.isNegative()) {
            throw new IllegalStateException(SECRETS_TTL_PROPERTY + " must be a positive duration");
        }
        if (services == null) {
            return;
        }
        for (Map.Entry<String, ExecutorServiceProperties> entry : services.entrySet()) {
            ExecutorServiceProperties properties = entry.getValue();
            if (properties == null || properties.getExecutionMode() == null) {
                throw new IllegalStateException("Executor " + entry.getKey() + ": execution-mode is required");
            }
            if (properties.getExecutionMode() == ExecutionMode.FULL_CONTROL
                    && !StringUtils.hasText(properties.getAddress())) {
                throw new IllegalStateException(
                        "Executor " + entry.getKey() + ": address is required when execution-mode is full-control");
            }
        }
    }

    public Duration getPollingInterval() {
        return pollingInterval;
    }

    public void setPollingInterval(Duration pollingInterval) {
        this.pollingInterval = pollingInterval;
    }

    public Duration getSecretsTtl() {
        return secretsTtl;
    }

    public void setSecretsTtl(Duration secretsTtl) {
        this.secretsTtl = secretsTtl;
    }

    public Duration getTtl() {
        return secretsTtl;
    }

    public Map<String, ExecutorServiceProperties> getServices() {
        return services;
    }

    public void setServices(Map<String, ExecutorServiceProperties> services) {
        this.services = services == null ? new LinkedHashMap<>() : services;
    }

    public Optional<ExecutorInfo> findExecutor(String name) {
        ExecutorServiceProperties properties = propertiesOf(name);
        if (properties == null) {
            return Optional.empty();
        }
        return Optional.of(new ExecutorInfo(name, properties.getExecutionMode()));
    }

    public Optional<String> findAddress(String name) {
        ExecutorServiceProperties properties = propertiesOf(name);
        if (properties == null || !StringUtils.hasText(properties.getAddress())) {
            return Optional.empty();
        }
        return Optional.of(properties.getAddress());
    }

    public int maxStatusReads(Duration secretsTtl) {
        Duration ttl = secretsTtl == null ? DEFAULT_SECRETS_TTL : secretsTtl;
        Duration every = pollingInterval == null ? DEFAULT_POLLING_INTERVAL : pollingInterval;
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

    private ExecutorServiceProperties propertiesOf(String name) {
        if (name == null || services == null) {
            return null;
        }
        return services.get(name);
    }

    public static class ExecutorServiceProperties {

        private String address;
        private ExecutionMode executionMode;

        public ExecutorServiceProperties() {
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public ExecutionMode getExecutionMode() {
            return executionMode;
        }

        public void setExecutionMode(ExecutionMode executionMode) {
            this.executionMode = executionMode;
        }
    }
}
