package org.opendatamesh.platform.pp.devops.old.v1.policyservice;

import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.devops.rest.v2.DevOpsApplicationIT;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.EventTypeRes;
import org.opendatamesh.platform.pp.devops.utils.usecases.NotificationEventHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "odm.product-plane.policy-service.active=true",
        "odm.product-plane.policy-service.version=2"
})
public class PolicyV1AdapterOtherVersionIT extends DevOpsApplicationIT {

    @Autowired
    private ApplicationContext context;

    /**
     * Feature: Only one answerer per request
     *
     * Scenario: Nobody answers when Policy is active at another version
     *   Given Policy is active at version 2
     *   Then neither the adapter nor the auto-approvers are registered
     */
    @Test
    public void whenPolicyActiveAtVersion2_thenNoHandlerIsRegistered() {
        // Given: Policy active at version 2 (test property source)

        // Then
        assertThat(context.getBeanNamesForType(PolicyClientV1.class)).isEmpty();
        assertThat(context.getBeansOfType(NotificationEventHandler.class).values())
                .noneMatch(h -> h.supportsEventType(EventTypeRes.ACTIVITY_EXECUTION_REQUESTED)
                        || h.supportsEventType(EventTypeRes.ACTIVITY_TASK_EXECUTION_REQUESTED));
    }
}
