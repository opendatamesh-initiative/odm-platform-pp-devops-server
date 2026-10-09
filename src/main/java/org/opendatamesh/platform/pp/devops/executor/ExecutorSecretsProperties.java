package org.opendatamesh.platform.pp.devops.executor;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "odm.utility-plane.executor-secrets")
public class ExecutorSecretsProperties {

    static final String PROPERTY = "odm.utility-plane.executor-secrets.ttl";

    private Duration ttl = Duration.ofHours(1);

    @PostConstruct
    public void validate() {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalStateException(PROPERTY + " must be a positive duration");
        }
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }
}
