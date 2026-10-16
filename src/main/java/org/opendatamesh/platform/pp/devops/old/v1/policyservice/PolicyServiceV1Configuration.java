package org.opendatamesh.platform.pp.devops.old.v1.policyservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.client.notification.NotificationClient;
import org.opendatamesh.platform.pp.devops.utils.usecases.NotificationEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the Policy V1 adapter handlers only when Policy is active at version 1.
 * The input enricher reads the activities through the core {@link ActivityService}.
 */
@Configuration
@ConditionalOnProperty(name = "odm.product-plane.policy-service.active", havingValue = "true")
@ConditionalOnProperty(name = "odm.product-plane.policy-service.version", havingValue = "1")
public class PolicyServiceV1Configuration {
    private static final Logger logger = LoggerFactory.getLogger(PolicyServiceV1Configuration.class);

    @Bean
    public PolicyV1InputEnricher policyV1InputEnricher(ActivityService activityService, ObjectMapper objectMapper) {
        return new PolicyV1InputEnricher(activityService, objectMapper);
    }

    @Bean
    public NotificationEventHandler notificationEventHandlerActivityExecutionRequested(
            NotificationClient notificationClient,
            PolicyClientV1 policyClient,
            PolicyV1InputEnricher inputEnricher) {
        logger.info("Registering NotificationEventHandlerActivityExecutionRequested");
        return new NotificationEventHandlerActivityExecutionRequested(notificationClient, policyClient, inputEnricher);
    }

    @Bean
    public NotificationEventHandler notificationEventHandlerActivityTaskExecutionRequested(
            NotificationClient notificationClient,
            PolicyClientV1 policyClient,
            PolicyV1InputEnricher inputEnricher) {
        logger.info("Registering NotificationEventHandlerActivityTaskExecutionRequested");
        return new NotificationEventHandlerActivityTaskExecutionRequested(notificationClient, policyClient, inputEnricher);
    }
}
