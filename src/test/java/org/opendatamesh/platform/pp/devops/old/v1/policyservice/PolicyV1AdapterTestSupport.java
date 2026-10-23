package org.opendatamesh.platform.pp.devops.old.v1.policyservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.notification.NotificationDispatchRes;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** Event builders shared by the Policy V1 adapter integration tests. */
final class PolicyV1AdapterTestSupport {
    static final String FQN = "urn:org.opendatamesh:dataproducts:sample-product";
    static final String VERSION_TAG = "1.0.0";
    static final ObjectMapper MAPPER = new ObjectMapper();

    private PolicyV1AdapterTestSupport() {
    }

    /** The V2 activity as an ACTIVITY_EXECUTION_REQUESTED event carries it: no tasks. */
    static ObjectNode activityEventContent(ActivityRes persisted) {
        ObjectNode content = MAPPER.createObjectNode();
        content.set("activity", activityNode(persisted));
        return content;
    }

    /** The V2 activity and the requested task, as an ACTIVITY_TASK_EXECUTION_REQUESTED event carries them. */
    static ObjectNode taskEventContent(ActivityRes persisted, TaskRes task) {
        ObjectNode content = activityEventContent(persisted);
        ObjectNode taskNode = content.putObject("task");
        taskNode.put("uuid", task.getUuid());
        taskNode.put("activityUuid", persisted.getUuid());
        taskNode.put("name", task.getName());
        taskNode.put("sortOrder", task.getSortOrder());
        taskNode.put("status", "PENDING");
        return content;
    }

    private static ObjectNode activityNode(ActivityRes persisted) {
        ObjectNode activity = MAPPER.createObjectNode();
        activity.put("uuid", persisted.getUuid());
        activity.put("dataProductVersionUuid", persisted.getDataProductVersionUuid());
        activity.put("dataProductFqn", FQN);
        activity.put("dataProductVersionTag", VERSION_TAG);
        activity.put("name", persisted.getName());
        activity.put("sortOrder", persisted.getSortOrder());
        activity.put("status", persisted.getStatus().name());
        return activity;
    }

    static void post(TestRestTemplate rest, String url, long sequenceId, String type, ObjectNode content) {
        NotificationDispatchRes notification = new NotificationDispatchRes();
        notification.setSequenceId(sequenceId);
        NotificationDispatchRes.NotificationDispatchEventRes event = new NotificationDispatchRes.NotificationDispatchEventRes();
        event.setType(type);
        event.setResourceType("ACTIVITY");
        event.setResourceIdentifier("ignored");
        event.setEventTypeVersion("V2.0.0");
        event.setEventContent(content);
        notification.setEvent(event);
        ResponseEntity<String> response = rest.postForEntity(url, notification, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
