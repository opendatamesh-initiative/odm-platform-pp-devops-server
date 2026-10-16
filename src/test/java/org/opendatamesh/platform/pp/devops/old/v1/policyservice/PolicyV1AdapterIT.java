package org.opendatamesh.platform.pp.devops.old.v1.policyservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientException;
import org.opendatamesh.platform.pp.devops.rest.v2.DevOpsApplicationIT;
import org.opendatamesh.platform.pp.devops.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskLogRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskResultRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.autoapprove.EmittedEventActivityExecutionApprovedRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.autoapprove.EmittedEventActivityTaskExecutionApprovedRes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendatamesh.platform.pp.devops.old.v1.policyservice.PolicyV1AdapterTestSupport.*;

@TestPropertySource(properties = {
        "odm.product-plane.policy-service.active=true",
        "odm.product-plane.policy-service.version=1",
        "odm.product-plane.policy-service.address=http://localhost:9999"
})
@Import(PolicyV1AdapterIT.PolicyClientTestConfig.class)
public class PolicyV1AdapterIT extends DevOpsApplicationIT {

    /** Dates in the Policy document use the format of the DevOps events, ISO-8601 with milliseconds and an offset. */
    private static final String ISO_DATE = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}(Z|[+-]\\d{2}:\\d{2})";

    @TestConfiguration
    static class PolicyClientTestConfig {
        @Bean
        @Primary
        public PolicyClientV1 mockPolicyClientV1() {
            return mock(PolicyClientV1.class);
        }
    }

    @Autowired
    private PolicyClientV1 policyClient;

    private long sequence = 1L;

    @BeforeEach
    void resetPolicy() {
        reset(policyClient);
        sequence = 1L;
    }

    /**
     * Feature: Activity execution request is evaluated by Policy V1
     *
     * Scenario: Activity request approved when no policy fails
     *   Given Policy is active at version 1
     *   And Policy returns no failed policy
     *   When DevOps receives an ACTIVITY_EXECUTION_REQUESTED event
     *   Then Policy is called with event ACTIVITY_EXECUTION_RESULT
     *   And an ACTIVITY_EXECUTION_APPROVED event is emitted for the activity
     */
    @Test
    public void whenActivityExecutionRequested_andNoPolicyFails_thenApprovedEmitted() {
        // Given
        ActivityRes activity = persistActivity("dpv-approve", "prod", 3, ExecutionStatus.PENDING);
        when(policyClient.validateInput(any(), anyBoolean())).thenReturn(response(result(true, true)));

        // When
        postActivityRequested(activityEventContent(activity));

        // Then
        PolicyResPolicyEvaluationRequest request = capturedRequest();
        assertThat(request.getEvent()).isEqualTo(PolicyResPolicyEvaluationRequest.EventType.ACTIVITY_EXECUTION_RESULT);
        assertThat(request.getResourceType()).isEqualTo(PolicyResPolicyEvaluationRequest.ResourceType.ACTIVITY_EXECUTION_RESULT);
        assertActivityApproved(activity.getUuid());
    }

    /**
     * Feature: Activity execution request is evaluated by Policy V1
     *
     * Scenario: Activity request rejected when a blocking policy fails
     *   Given Policy is active at version 1
     *   And Policy returns a failed policy with blockingFlag true
     *   When DevOps receives an ACTIVITY_EXECUTION_REQUESTED event
     *   Then an ACTIVITY_EXECUTION_REJECTED event is emitted for the activity
     */
    @Test
    public void whenActivityExecutionRequested_andBlockingPolicyFails_thenRejectedEmitted() {
        // Given
        ActivityRes activity = persistActivity("dpv-reject", "prod", 3, ExecutionStatus.PENDING);
        when(policyClient.validateInput(any(), anyBoolean())).thenReturn(response(result(false, true)));

        // When
        postActivityRequested(activityEventContent(activity));

        // Then
        assertActivityRejected(activity.getUuid());
    }

    /**
     * Feature: Activity execution request is evaluated by Policy V1
     *
     * Scenario: Activity request approved when only a non-blocking policy fails
     *   Given Policy is active at version 1
     *   And Policy returns a failed policy with blockingFlag false
     *   When DevOps receives an ACTIVITY_EXECUTION_REQUESTED event
     *   Then an ACTIVITY_EXECUTION_APPROVED event is emitted for the activity
     */
    @Test
    public void whenActivityExecutionRequested_andNonBlockingPolicyFails_thenApprovedEmitted() {
        // Given
        ActivityRes activity = persistActivity("dpv-nonblocking", "prod", 3, ExecutionStatus.PENDING);
        when(policyClient.validateInput(any(), anyBoolean())).thenReturn(response(result(false, false)));

        // When
        postActivityRequested(activityEventContent(activity));

        // Then
        assertActivityApproved(activity.getUuid());
    }

    /**
     * Feature: Activity execution request is evaluated by Policy V1
     *
     * Scenario: Activity request approved when Policy returns no results
     *   Given Policy is active at version 1
     *   And Policy returns an empty or null result list
     *   When DevOps receives an ACTIVITY_EXECUTION_REQUESTED event
     *   Then an ACTIVITY_EXECUTION_APPROVED event is emitted for the activity
     */
    @Test
    public void whenActivityExecutionRequested_andNoResults_thenApprovedEmitted() {
        // Given: an empty list first, then a null list
        ActivityRes activity = persistActivity("dpv-noresults", "prod", 3, ExecutionStatus.PENDING);
        when(policyClient.validateInput(any(), anyBoolean()))
                .thenReturn(response())
                .thenReturn(new PolicyResValidationResponse());

        // When
        postActivityRequested(activityEventContent(activity));
        postActivityRequested(activityEventContent(activity));

        // Then
        List<Object> events = notifiedEvents();
        assertThat(events).hasSize(2).allMatch(e -> e instanceof EmittedEventActivityExecutionApprovedRes);
    }

    /**
     * Feature: Activity execution request is evaluated by Policy V1
     *
     * Scenario: Activity request rejected when Policy cannot be reached
     *   Given Policy is active at version 1
     *   And the Policy client throws a ClientException
     *   When DevOps receives an ACTIVITY_EXECUTION_REQUESTED event
     *   Then an ACTIVITY_EXECUTION_REJECTED event is emitted for the activity
     */
    @Test
    public void whenActivityExecutionRequested_andPolicyUnreachable_thenRejectedEmitted() {
        // Given
        ActivityRes activity = persistActivity("dpv-unreachable", "prod", 3, ExecutionStatus.PENDING);
        when(policyClient.validateInput(any(), anyBoolean())).thenThrow(new ClientException(503, "unavailable"));

        // When
        postActivityRequested(activityEventContent(activity));

        // Then
        assertActivityRejected(activity.getUuid());
    }

    /**
     * Feature: Task execution request is evaluated by Policy V1
     *
     * Scenario: Task request approved when no policy fails
     *   Given Policy is active at version 1
     *   And Policy returns no failed policy
     *   When DevOps receives an ACTIVITY_TASK_EXECUTION_REQUESTED event
     *   Then Policy is called with event TASK_EXECUTION_RESULT
     *   And an ACTIVITY_TASK_EXECUTION_APPROVED event is emitted for the activity and task
     */
    @Test
    public void whenTaskExecutionRequested_andNoPolicyFails_thenApprovedEmitted() {
        // Given
        ActivityRes activity = persistActivityWithTasks("dpv-task-approve", "prod", taskWithOutputs("deploy", 1));
        when(policyClient.validateInput(any(), anyBoolean())).thenReturn(response(result(true, true)));

        // When
        postTaskRequested(activity, activity.getTasks().get(0));

        // Then
        PolicyResPolicyEvaluationRequest request = capturedRequest();
        assertThat(request.getEvent()).isEqualTo(PolicyResPolicyEvaluationRequest.EventType.TASK_EXECUTION_RESULT);
        assertThat(request.getResourceType()).isEqualTo(PolicyResPolicyEvaluationRequest.ResourceType.TASK_EXECUTION_RESULT);
        assertTaskApproved(activity.getUuid(), activity.getTasks().get(0).getUuid());
    }

    /**
     * Feature: Task execution request is evaluated by Policy V1
     *
     * Scenario: Task request rejected when a blocking policy fails
     *   Given Policy is active at version 1
     *   And Policy returns a failed policy with blockingFlag true
     *   When DevOps receives an ACTIVITY_TASK_EXECUTION_REQUESTED event
     *   Then an ACTIVITY_TASK_EXECUTION_REJECTED event is emitted for the activity and task
     */
    @Test
    public void whenTaskExecutionRequested_andBlockingPolicyFails_thenRejectedEmitted() {
        // Given
        ActivityRes activity = persistActivityWithTasks("dpv-task-reject", "prod", taskWithOutputs("deploy", 1));
        when(policyClient.validateInput(any(), anyBoolean())).thenReturn(response(result(false, true)));

        // When
        postTaskRequested(activity, activity.getTasks().get(0));

        // Then
        assertTaskRejected(activity.getUuid(), activity.getTasks().get(0).getUuid());
    }

    /**
     * Feature: Task execution request is evaluated by Policy V1
     *
     * Scenario: Task request rejected when Policy cannot be reached
     *   Given Policy is active at version 1
     *   And the Policy client throws a ClientException
     *   When DevOps receives an ACTIVITY_TASK_EXECUTION_REQUESTED event
     *   Then an ACTIVITY_TASK_EXECUTION_REJECTED event is emitted for the activity and task
     */
    @Test
    public void whenTaskExecutionRequested_andPolicyUnreachable_thenRejectedEmitted() {
        // Given
        ActivityRes activity = persistActivityWithTasks("dpv-task-unreachable", "prod", taskWithOutputs("deploy", 1));
        when(policyClient.validateInput(any(), anyBoolean())).thenThrow(new ClientException(503, "unavailable"));

        // When
        postTaskRequested(activity, activity.getTasks().get(0));

        // Then
        assertTaskRejected(activity.getUuid(), activity.getTasks().get(0).getUuid());
    }

    /**
     * Feature: The Policy document is the V2 event content with the enrichment
     *
     * Scenario: Activity afterState is the V2 activity with its siblings, and currentState is absent
     *   Given Policy is active at version 1
     *   When DevOps receives an ACTIVITY_EXECUTION_REQUESTED event whose activity is PENDING
     *   Then the Policy request afterState has the activity exactly as the event carries it
     *   And the activity status inside afterState is still PENDING
     *   And the afterState has exactly the fields activity and otherActivities
     *   And the Policy request currentState is absent
     */
    @Test
    public void whenActivityExecutionRequested_thenAfterStateIsPolicyInputWithEventActivity() {
        // Given
        ActivityRes activity = persistActivity("dpv-after-state", "prod", 3, ExecutionStatus.PENDING);
        when(policyClient.validateInput(any(), anyBoolean())).thenReturn(response());
        ObjectNode content = activityEventContent(activity);

        // When
        postActivityRequested(content);

        // Then
        PolicyResPolicyEvaluationRequest request = capturedRequest();
        JsonNode afterState = request.getAfterState();
        assertThat(afterState.get("activity")).isEqualTo(content.get("activity"));
        assertThat(afterState.get("activity").get("status").asText()).isEqualTo("PENDING");
        assertThat(afterState.fieldNames()).toIterable().containsExactlyInAnyOrder("activity", "otherActivities");
        assertThat(request.getCurrentState()).isNull();
    }

    /**
     * Feature: The Policy document is the V2 event content with the enrichment
     *
     * Scenario: Task afterState carries the activity with all tasks and the requested task unchanged
     *   Given Policy is active at version 1
     *   When DevOps receives an ACTIVITY_TASK_EXECUTION_REQUESTED event
     *   Then the Policy request afterState task equals the task of the event
     *   And the afterState activity equals the event activity plus its tasks
     */
    @Test
    public void whenTaskExecutionRequested_thenAfterStateCarriesActivityTasksAndRequestedTaskUnchanged() {
        // Given
        ActivityRes activity = persistActivityWithTasks("dpv-task-after-state", "prod", taskWithOutputs("deploy", 1), taskWithOutputs("publish", 2));
        when(policyClient.validateInput(any(), anyBoolean())).thenReturn(response());
        ObjectNode content = taskEventContent(activity, activity.getTasks().get(0));

        // When
        postTaskRequested(content);

        // Then
        JsonNode afterState = capturedRequest().getAfterState();
        assertThat(afterState.get("task")).isEqualTo(content.get("task"));
        assertThat(afterState.get("task").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("uuid", "activityUuid", "name", "sortOrder", "status");
        assertThat(withoutTasks(afterState.get("activity"))).isEqualTo(content.get("activity"));
        assertThat(afterState.get("activity").get("tasks")).hasSize(2);
    }

    /**
     * Feature: The Policy document is the V2 event content with the enrichment
     *
     * Scenario: Policy request carries the legacy product identifiers
     *   Given an activity on data product fqn "urn:org.opendatamesh:dataproducts:sample-product" and version tag "1.0.0"
     *   When DevOps receives an ACTIVITY_EXECUTION_REQUESTED event
     *   Then the Policy request dataProductId is "a72bbe0c-18c6-30b0-a03a-5392d6a770ea"
     *   And the Policy request dataProductVersion is "1.0.0"
     */
    @Test
    public void whenActivityExecutionRequested_thenRequestCarriesLegacyIdAndVersionTag() {
        // Given
        ActivityRes activity = persistActivity("dpv-legacy-id", "prod", 3, ExecutionStatus.PENDING);
        when(policyClient.validateInput(any(), anyBoolean())).thenReturn(response());

        // When
        postActivityRequested(activityEventContent(activity));

        // Then
        PolicyResPolicyEvaluationRequest request = capturedRequest();
        assertThat(request.getDataProductId()).isEqualTo("a72bbe0c-18c6-30b0-a03a-5392d6a770ea");
        assertThat(request.getDataProductVersion()).isEqualTo(VERSION_TAG);
    }

    /**
     * Feature: Other activities of the same data product version
     *
     * Scenario: otherActivities holds the siblings of the same version, without the requested activity
     *   Given activity "prod" and activities "dev" and "qa" of the same data product version
     *   And activity "dev" of another data product version
     *   When DevOps receives an ACTIVITY_EXECUTION_REQUESTED event for "prod"
     *   Then otherActivities lists "dev" and "qa" only
     */
    @Test
    public void whenActivityExecutionRequested_thenOtherActivitiesHoldSameVersionSiblingsOnly() {
        // Given
        String dataProductVersionUuid = "dpv-siblings";
        ActivityRes prod = persistActivity(dataProductVersionUuid, "prod", 2, ExecutionStatus.PENDING);
        persistActivity(dataProductVersionUuid, "dev", 1, ExecutionStatus.SUCCEEDED);
        persistActivity(dataProductVersionUuid, "qa", 3, ExecutionStatus.RUNNING);
        persistActivity("dpv-other-version", "dev", 1, ExecutionStatus.SUCCEEDED);
        when(policyClient.validateInput(any(), anyBoolean())).thenReturn(response());

        // When
        postActivityRequested(activityEventContent(prod));

        // Then
        JsonNode others = capturedRequest().getAfterState().get("otherActivities");
        assertThat(names(others)).containsExactly("dev", "qa");
        assertThat(others.findValuesAsText("uuid")).doesNotContain(prod.getUuid());
    }

    /**
     * Feature: Other activities of the same data product version
     *
     * Scenario: Each other activity carries the summary fields only, with the V2 status and ISO dates
     *   Given a sibling activity "dev" that succeeded and has a start and an end time
     *   When DevOps receives an ACTIVITY_EXECUTION_REQUESTED event for another activity of the version
     *   Then the summary of "dev" has exactly the fields uuid, name, sortOrder, status, startedAt, and finishedAt
     *   And its status is SUCCEEDED and its dates are ISO-8601 text
     */
    @Test
    public void whenActivityExecutionRequested_thenOtherActivitySummaryHasSummaryFieldsAndIsoDates() {
        // Given
        String dataProductVersionUuid = "dpv-summary";
        ActivityRes prod = persistActivity(dataProductVersionUuid, "prod", 2, ExecutionStatus.PENDING);
        ActivityRes dev = newActivity(dataProductVersionUuid, "dev", 1, ExecutionStatus.SUCCEEDED);
        dev.setStartedAt(new Date(1760000000000L));
        dev.setFinishedAt(new Date(1760000600000L));
        persistResource(dev);
        when(policyClient.validateInput(any(), anyBoolean())).thenReturn(response());

        // When
        postActivityRequested(activityEventContent(prod));

        // Then
        JsonNode summary = capturedRequest().getAfterState().get("otherActivities").get(0);
        assertThat(summary.fieldNames()).toIterable()
                .containsExactly("uuid", "name", "sortOrder", "status", "startedAt", "finishedAt");
        assertThat(summary.get("name").asText()).isEqualTo("dev");
        assertThat(summary.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(summary.get("startedAt").isTextual()).isTrue();
        assertThat(summary.get("startedAt").asText()).matches(ISO_DATE);
        assertThat(summary.get("finishedAt").asText()).matches(ISO_DATE);
    }

    /**
     * Feature: Other activities of the same data product version
     *
     * Scenario: otherActivities is ordered by sortOrder with nulls last
     *   Given sibling activities with sortOrder 3, no sortOrder, and sortOrder 1
     *   When DevOps receives an ACTIVITY_EXECUTION_REQUESTED event for another activity of the version
     *   Then otherActivities lists the sortOrder 1 activity, then the sortOrder 3 activity, then the one without sortOrder
     */
    @Test
    public void whenActivityExecutionRequested_thenOtherActivitiesOrderedBySortOrderNullsLast() {
        // Given
        String dataProductVersionUuid = "dpv-order";
        ActivityRes prod = persistActivity(dataProductVersionUuid, "prod", 2, ExecutionStatus.PENDING);
        persistActivity(dataProductVersionUuid, "qa", 3, ExecutionStatus.PENDING);
        persistActivity(dataProductVersionUuid, "unordered", null, ExecutionStatus.PENDING);
        persistActivity(dataProductVersionUuid, "dev", 1, ExecutionStatus.SUCCEEDED);
        when(policyClient.validateInput(any(), anyBoolean())).thenReturn(response());

        // When
        postActivityRequested(activityEventContent(prod));

        // Then
        assertThat(names(capturedRequest().getAfterState().get("otherActivities")))
                .containsExactly("dev", "qa", "unordered");
    }

    /**
     * Feature: Other activities of the same data product version
     *
     * Scenario: otherActivities is an empty list when the requested activity is alone in its version
     *   Given the requested activity is the only activity of its data product version
     *   When DevOps receives an ACTIVITY_EXECUTION_REQUESTED event
     *   Then otherActivities is present and empty
     */
    @Test
    public void whenActivityExecutionRequested_andNoSiblings_thenOtherActivitiesIsEmptyArray() {
        // Given
        ActivityRes prod = persistActivity("dpv-alone", "prod", 2, ExecutionStatus.PENDING);
        when(policyClient.validateInput(any(), anyBoolean())).thenReturn(response());

        // When
        postActivityRequested(activityEventContent(prod));

        // Then
        JsonNode others = capturedRequest().getAfterState().get("otherActivities");
        assertThat(others).isNotNull();
        assertThat(others.isArray()).isTrue();
        assertThat(others).isEmpty();
    }

    /**
     * Feature: Tasks of the activity in a task request
     *
     * Scenario: activity.tasks holds all tasks in sortOrder, with results and without logs
     *   Given an activity with tasks "publish" (sortOrder 2) and "deploy" (sortOrder 1), each with a log and a result
     *   When DevOps receives an ACTIVITY_TASK_EXECUTION_REQUESTED event for "deploy"
     *   Then activity.tasks lists "deploy" then "publish"
     *   And each task has its results with the fields uuid, content, and generatedAt, and has no logs field
     */
    @Test
    public void whenTaskExecutionRequested_thenActivityTasksHaveResultsWithoutLogsInOrder() {
        // Given
        ActivityRes activity = persistActivityWithTasks("dpv-tasks-enriched", "prod", taskWithOutputs("publish", 2), taskWithOutputs("deploy", 1));
        when(policyClient.validateInput(any(), anyBoolean())).thenReturn(response());
        TaskRes deploy = taskNamed(activity, "deploy");

        // When
        postTaskRequested(activity, deploy);

        // Then
        JsonNode tasks = capturedRequest().getAfterState().get("activity").get("tasks");
        assertThat(names(tasks)).containsExactly("deploy", "publish");
        for (JsonNode task : tasks) {
            assertThat(task.has("logs")).isFalse();
            assertThat(task.get("results")).hasSize(1);
            JsonNode result = task.get("results").get(0);
            assertThat(result.fieldNames()).toIterable().containsExactlyInAnyOrder("uuid", "content", "generatedAt");
            assertThat(result.get("content").asText()).startsWith("result-of-");
            assertThat(result.get("generatedAt").asText()).matches(ISO_DATE);
        }
    }

    /**
     * Feature: Tasks of the activity in a task request
     *
     * Scenario: activity.tasks includes the requested task and an activity without tasks gives an empty list
     *   Given an activity with one task "deploy" that has no results
     *   When DevOps receives an ACTIVITY_TASK_EXECUTION_REQUESTED event for "deploy"
     *   Then activity.tasks lists "deploy" with an empty results list and no logs field
     */
    @Test
    public void whenTaskExecutionRequested_thenRequestedTaskIsInActivityTasksWithEmptyResults() {
        // Given
        ActivityRes activity = persistActivityWithTasks("dpv-task-no-results", "prod", taskWithoutOutputs("deploy", 1));
        when(policyClient.validateInput(any(), anyBoolean())).thenReturn(response());

        // When
        postTaskRequested(activity, activity.getTasks().get(0));

        // Then
        JsonNode tasks = capturedRequest().getAfterState().get("activity").get("tasks");
        assertThat(names(tasks)).containsExactly("deploy");
        assertThat(tasks.get(0).get("uuid").asText()).isEqualTo(activity.getTasks().get(0).getUuid());
        assertThat(tasks.get(0).get("results")).isEmpty();
        assertThat(tasks.get(0).has("logs")).isFalse();
    }

    /**
     * Feature: Activity missing for a task request is a defect
     *
     * Scenario: Task request for an unknown activity fails the notification and emits no decision
     *   Given no activity exists with the uuid of the request
     *   When DevOps receives an ACTIVITY_TASK_EXECUTION_REQUESTED event for that activity
     *   Then the notification is marked as failed
     *   And no decision event is emitted
     *   And Policy is not called
     */
    @Test
    public void whenTaskExecutionRequested_andActivityMissing_thenNotificationFailsWithoutDecision() {
        // Given
        ActivityRes unknown = newActivity("dpv-missing", "prod", 2, ExecutionStatus.PENDING);
        unknown.setUuid("00000000-0000-0000-0000-000000000000");
        TaskRes task = new TaskRes();
        task.setUuid("00000000-0000-0000-0000-000000000001");
        task.setName("deploy");
        task.setSortOrder(1);
        ObjectNode content = taskEventContent(unknown, task);
        long sequenceId = sequence++;

        // When
        post(rest, apiUrl(RoutesV2.OBSERVER_NOTIFICATIONS), sequenceId, "ACTIVITY_TASK_EXECUTION_REQUESTED", content);

        // Then
        verify(notificationClient).processingFailure(sequenceId);
        assertThat(notifiedEvents()).isEmpty();
        verify(policyClient, never()).validateInput(any(), anyBoolean());
    }

    /**
     * Feature: Activity missing for a task request is a defect
     *
     * Scenario: Activity request without a data product version fails the notification
     *   Given an ACTIVITY_EXECUTION_REQUESTED event whose activity has no dataProductVersionUuid
     *   When DevOps receives the event
     *   Then the notification is marked as failed
     *   And Policy is not called
     */
    @Test
    public void whenActivityExecutionRequested_andNoDataProductVersion_thenNotificationFails() {
        // Given
        ActivityRes activity = newActivity(null, "prod", 2, ExecutionStatus.PENDING);
        activity.setUuid("00000000-0000-0000-0000-000000000002");
        ObjectNode content = activityEventContent(activity);
        long sequenceId = sequence++;

        // When
        post(rest, apiUrl(RoutesV2.OBSERVER_NOTIFICATIONS), sequenceId, "ACTIVITY_EXECUTION_REQUESTED", content);

        // Then
        verify(notificationClient).processingFailure(sequenceId);
        assertThat(notifiedEvents()).isEmpty();
        verify(policyClient, never()).validateInput(any(), anyBoolean());
    }

    // ---- helpers ----

    private void postActivityRequested(ObjectNode content) {
        post(rest, apiUrl(RoutesV2.OBSERVER_NOTIFICATIONS), sequence++, "ACTIVITY_EXECUTION_REQUESTED", content);
    }

    private void postActivityRequested(ActivityRes activity) {
        postActivityRequested(activityEventContent(activity));
    }

    private void postTaskRequested(ObjectNode content) {
        post(rest, apiUrl(RoutesV2.OBSERVER_NOTIFICATIONS), sequence++, "ACTIVITY_TASK_EXECUTION_REQUESTED", content);
    }

    private void postTaskRequested(ActivityRes activity, TaskRes task) {
        postTaskRequested(taskEventContent(activity, task));
    }

    private PolicyResPolicyEvaluationRequest capturedRequest() {
        ArgumentCaptor<PolicyResPolicyEvaluationRequest> captor = ArgumentCaptor.forClass(PolicyResPolicyEvaluationRequest.class);
        verify(policyClient).validateInput(captor.capture(), eq(true));
        return captor.getValue();
    }

    private ActivityRes persistActivity(String dataProductVersionUuid, String name, Integer sortOrder, ExecutionStatus status) {
        return persistResource(newActivity(dataProductVersionUuid, name, sortOrder, status));
    }

    private ActivityRes persistActivityWithTasks(String dataProductVersionUuid, String name, TaskRes... tasks) {
        ActivityRes activity = newActivity(dataProductVersionUuid, name, 2, ExecutionStatus.PENDING);
        activity.setTasks(List.of(tasks));
        return persistResource(activity);
    }

    /** A pending task with one log and one result. */
    private static TaskRes taskWithOutputs(String name, int sortOrder) {
        TaskRes task = taskWithoutOutputs(name, sortOrder);
        TaskLogRes log = new TaskLogRes();
        log.setContent("log-of-" + name);
        log.setGeneratedAt(new Date(1760000000000L));
        TaskResultRes result = new TaskResultRes();
        result.setContent("result-of-" + name);
        result.setGeneratedAt(new Date(1760000100000L));
        task.setLogs(List.of(log));
        task.setResults(List.of(result));
        return task;
    }

    private static TaskRes taskWithoutOutputs(String name, int sortOrder) {
        TaskRes task = new TaskRes();
        task.setName(name);
        task.setSortOrder(sortOrder);
        task.setStatus(ExecutionStatus.PENDING);
        return task;
    }

    private ActivityRes persistResource(ActivityRes payload) {
        ResponseEntity<ActivityRes> response = rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES),
                new HttpEntity<>(payload),
                ActivityRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private static ActivityRes newActivity(String dataProductVersionUuid, String name, Integer sortOrder, ExecutionStatus status) {
        ActivityRes activity = new ActivityRes();
        activity.setDataProductVersionUuid(dataProductVersionUuid);
        activity.setDataProductFqn(FQN);
        activity.setDataProductVersionTag(VERSION_TAG);
        activity.setName(name);
        activity.setSortOrder(sortOrder);
        activity.setStatus(status);
        return activity;
    }

    private static TaskRes taskNamed(ActivityRes activity, String name) {
        return activity.getTasks().stream()
                .filter(task -> name.equals(task.getName()))
                .findFirst()
                .orElseThrow();
    }

    private static JsonNode withoutTasks(JsonNode activity) {
        ObjectNode copy = activity.deepCopy();
        copy.remove("tasks");
        return copy;
    }

    private static List<String> names(JsonNode items) {
        return StreamSupport.stream(items.spliterator(), false)
                .map(item -> item.get("name").asText())
                .toList();
    }

    private static PolicyResValidationResponse response(PolicyResPolicyEvaluationResult... results) {
        PolicyResValidationResponse response = new PolicyResValidationResponse();
        response.setPolicyResults(results.length == 0 ? Collections.emptyList() : List.of(results));
        return response;
    }

    private static PolicyResPolicyEvaluationResult result(boolean passed, boolean blocking) {
        PolicyResPolicy policy = new PolicyResPolicy();
        policy.setBlockingFlag(blocking);
        PolicyResPolicyEvaluationResult result = new PolicyResPolicyEvaluationResult();
        result.setResult(passed);
        result.setPolicy(policy);
        return result;
    }

    private Object singleEvent() {
        List<Object> events = notifiedEvents();
        assertThat(events).hasSize(1);
        return events.get(0);
    }

    private void assertActivityApproved(String activityUuid) {
        Object event = singleEvent();
        assertThat(event).isInstanceOf(EmittedEventActivityExecutionApprovedRes.class);
        EmittedEventActivityExecutionApprovedRes approved = (EmittedEventActivityExecutionApprovedRes) event;
        assertThat(approved.getResourceIdentifier()).isEqualTo(activityUuid);
        assertThat(approved.getEventContent().getActivity().getUuid()).isEqualTo(activityUuid);
        assertThat(approved.getEventContent().getActivity().getSortOrder()).isEqualTo(3);
    }

    private void assertActivityRejected(String activityUuid) {
        Object event = singleEvent();
        assertThat(event).isInstanceOf(EventEmittedActivityExecutionRejected.class);
        EventEmittedActivityExecutionRejected rejected = (EventEmittedActivityExecutionRejected) event;
        assertThat(rejected.getType().name()).isEqualTo("ACTIVITY_EXECUTION_REJECTED");
        assertThat(rejected.getResourceIdentifier()).isEqualTo(activityUuid);
        assertThat(rejected.getEventContent().getActivity().getUuid()).isEqualTo(activityUuid);
        assertThat(rejected.getEventContent().getActivity().getName()).isEqualTo("prod");
    }

    private void assertTaskApproved(String activityUuid, String taskUuid) {
        Object event = singleEvent();
        assertThat(event).isInstanceOf(EmittedEventActivityTaskExecutionApprovedRes.class);
        EmittedEventActivityTaskExecutionApprovedRes approved = (EmittedEventActivityTaskExecutionApprovedRes) event;
        assertThat(approved.getResourceIdentifier()).isEqualTo(activityUuid);
        assertThat(approved.getEventContent().getActivity().getUuid()).isEqualTo(activityUuid);
        assertThat(approved.getEventContent().getTask().getUuid()).isEqualTo(taskUuid);
    }

    private void assertTaskRejected(String activityUuid, String taskUuid) {
        Object event = singleEvent();
        assertThat(event).isInstanceOf(EventEmittedActivityTaskExecutionRejected.class);
        EventEmittedActivityTaskExecutionRejected rejected = (EventEmittedActivityTaskExecutionRejected) event;
        assertThat(rejected.getType().name()).isEqualTo("ACTIVITY_TASK_EXECUTION_REJECTED");
        assertThat(rejected.getResourceIdentifier()).isEqualTo(activityUuid);
        assertThat(rejected.getEventContent().getActivity().getUuid()).isEqualTo(activityUuid);
        assertThat(rejected.getEventContent().getTask().getUuid()).isEqualTo(taskUuid);
        assertThat(rejected.getEventContent().getTask().getName()).isEqualTo("deploy");
    }
}
