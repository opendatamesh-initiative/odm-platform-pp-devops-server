package org.opendatamesh.platform.pp.devops.old.v1.policyservice;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.devops.rest.v2.DevOpsApplicationIT;
import org.opendatamesh.platform.pp.devops.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.autoapprove.EmittedEventActivityExecutionApprovedRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.autoapprove.EmittedEventActivityTaskExecutionApprovedRes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.opendatamesh.platform.pp.devops.old.v1.policyservice.PolicyV1AdapterTestSupport.MAPPER;
import static org.opendatamesh.platform.pp.devops.old.v1.policyservice.PolicyV1AdapterTestSupport.post;
import static org.assertj.core.api.Assertions.assertThat;

public class PolicyV1AdapterInactiveIT extends DevOpsApplicationIT {

    @Autowired
    private ApplicationContext context;

    /**
     * Feature: Only one answerer per request
     *
     * Scenario: Requests are auto-approved when Policy is inactive
     *   Given Policy is inactive
     *   When DevOps receives an ACTIVITY_EXECUTION_REQUESTED event
     *   And DevOps receives an ACTIVITY_TASK_EXECUTION_REQUESTED event
     *   Then both are approved without calling Policy
     *   And no Policy V1 adapter bean exists
     */
    @Test
    public void whenPolicyInactive_thenBothRequestsAutoApprovedWithoutPolicyCall() {
        // Given: default configuration, Policy inactive
        ObjectNode activity = MAPPER.createObjectNode();
        activity.put("uuid", "activity-1");
        activity.put("dataProductVersionUuid", "dpv-1");
        activity.put("dataProductFqn", "urn:org.opendatamesh:dataproducts:sample-product");
        activity.put("dataProductVersionTag", "1.0.0");
        activity.put("name", "prod");
        activity.put("sortOrder", 3);
        activity.put("status", "PENDING");
        ObjectNode activityContent = MAPPER.createObjectNode();
        activityContent.set("activity", activity);
        ObjectNode taskContent = activityContent.deepCopy();
        taskContent.putObject("task").put("uuid", "task-1").put("name", "build");

        // When
        post(rest, apiUrl(RoutesV2.OBSERVER_NOTIFICATIONS), 1L, "ACTIVITY_EXECUTION_REQUESTED", activityContent);
        post(rest, apiUrl(RoutesV2.OBSERVER_NOTIFICATIONS), 2L, "ACTIVITY_TASK_EXECUTION_REQUESTED", taskContent);

        // Then
        List<Object> events = notifiedEvents();
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(EmittedEventActivityExecutionApprovedRes.class);
        assertThat(events.get(1)).isInstanceOf(EmittedEventActivityTaskExecutionApprovedRes.class);
        assertThat(context.getBeanNamesForType(PolicyClientV1.class)).isEmpty();
        assertThat(context.containsBean("notificationEventHandlerActivityExecutionRequested")).isFalse();
        assertThat(context.containsBean("notificationEventHandlerActivityTaskExecutionRequested")).isFalse();
    }
}
