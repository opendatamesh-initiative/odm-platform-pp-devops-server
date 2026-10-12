package org.opendatamesh.platform.pp.devops.rest.v2.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.GitRefType;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStatusRes;
import org.opendatamesh.platform.pp.devops.executor.ExecutorSecretsStore;
import org.opendatamesh.platform.pp.devops.rest.v2.ActivityHistoryFixture;
import org.opendatamesh.platform.pp.devops.rest.v2.DevOpsApplicationIT;
import org.opendatamesh.platform.pp.devops.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ExecutorParametersRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.GitRefRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.DataProductRepoRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskResultRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.emitted.EmittedEventActivityFailedRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.emitted.EmittedEventActivitySucceededRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.emitted.EmittedEventActivityTaskExecutionRequestedRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.execute.ActivityExecuteCommandRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.execute.ActivityExecuteResultRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.autoapprove.EmittedEventActivityExecutionApprovedRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.autoapprove.EmittedEventActivityTaskExecutionApprovedRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.notification.NotificationDispatchRes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-control approvals, task runs, placeholders, and the observer.
 * Scenarios trace to {@code spdd/prompt/BDMD-5437-202609290955-[Feat]-service-full-control-happy-path.md}.
 */
public class ActivityFullControlFlowIT extends DevOpsApplicationIT {



    @Autowired
    private ExecutorSecretsStore executorSecretsStore;

    @Autowired
    private ActivityHistoryFixture history;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private long sequence = 1L;

    @BeforeEach
    void resetState() {
        sequence = 1L;
    }

    /**
     * Feature: Executor secrets
     *
     * Scenario: Secrets are removed when the activity ends
     *   Given an activity with one task on "starter" executed with a secret header
     *   When the activity and the task are approved and the run succeeds
     *   Then the secrets store holds nothing for that activity
     */
    @Test
    public void whenActivityEndsThenSecretsRemoved() {
        // Given
        stubExecutor(status("SUCCEEDED"), "build log");
        ActivityRes created = executeOneTask("dpv-secret-end", "prod", "token-value");
        assertThat(executorSecretsStore.find("starter", created.getUuid())).containsEntry("x-odm-token", "token-value");

        // When
        postActivityApproved(created.getUuid());
        postTaskApproved(created.getUuid(), created.getTasks().get(0).getUuid());

        // Then
        assertThat(executorSecretsStore.find("starter", created.getUuid())).isEmpty();
        assertThat(executor().starts()).isNotEmpty();
    }

    /**
     * Feature: Auto-approval while the Policy service is inactive
     *
     * Scenario: An activity execution request is approved without checks
     *   Given the Policy service is inactive
     *   When DevOps receives ACTIVITY_EXECUTION_REQUESTED for an activity
     *   Then it emits ACTIVITY_EXECUTION_APPROVED with the activity uuid, name, sortOrder, and data product version uuid
     *   And the notification is marked processed
     */
    @Test
    public void whenActivityExecutionRequestedThenApprovedEmitted() {
        // Given
        ObjectNode activity = objectMapper.createObjectNode();
        activity.put("uuid", "activity-1");
        activity.put("name", "prod");
        activity.put("sortOrder", 2);
        activity.put("dataProductVersionUuid", "dpv-1");
        ObjectNode content = objectMapper.createObjectNode();
        content.set("activity", activity);

        // When
        long sequenceId = postNotification("ACTIVITY_EXECUTION_REQUESTED", content);

        // Then
        assertThat(notifiedEvents()).singleElement().isInstanceOf(EmittedEventActivityExecutionApprovedRes.class);
        EmittedEventActivityExecutionApprovedRes event =
                (EmittedEventActivityExecutionApprovedRes) notifiedEvents().get(0);
        assertThat(event.getResourceIdentifier()).isEqualTo("activity-1");
        assertThat(event.getEventContent().getActivity().getUuid()).isEqualTo("activity-1");
        assertThat(event.getEventContent().getActivity().getName()).isEqualTo("prod");
        assertThat(event.getEventContent().getActivity().getSortOrder()).isEqualTo(2);
        assertThat(event.getEventContent().getActivity().getDataProductVersionUuid()).isEqualTo("dpv-1");
        assertProcessingSucceeded(sequenceId);
    }

    /**
     * Feature: Auto-approval while the Policy service is inactive
     *
     * Scenario: A task execution request is approved without checks
     *   Given the Policy service is inactive
     *   When DevOps receives ACTIVITY_TASK_EXECUTION_REQUESTED for a task
     *   Then it emits ACTIVITY_TASK_EXECUTION_APPROVED with the activity block and the task uuid and name
     */
    @Test
    public void whenTaskExecutionRequestedThenApprovedEmitted() {
        // Given
        ObjectNode content = objectMapper.createObjectNode();
        ObjectNode activity = content.putObject("activity");
        activity.put("uuid", "activity-1");
        activity.put("name", "prod");
        activity.put("sortOrder", 4);
        activity.put("dataProductVersionUuid", "dpv-1");
        ObjectNode task = content.putObject("task");
        task.put("uuid", "task-1");
        task.put("name", "deploy");

        // When
        postNotification("ACTIVITY_TASK_EXECUTION_REQUESTED", content);

        // Then
        assertThat(notifiedEvents()).singleElement().isInstanceOf(EmittedEventActivityTaskExecutionApprovedRes.class);
        EmittedEventActivityTaskExecutionApprovedRes event =
                (EmittedEventActivityTaskExecutionApprovedRes) notifiedEvents().get(0);
        assertThat(event.getEventContent().getActivity().getUuid()).isEqualTo("activity-1");
        assertThat(event.getEventContent().getActivity().getName()).isEqualTo("prod");
        assertThat(event.getEventContent().getActivity().getSortOrder()).isEqualTo(4);
        assertThat(event.getEventContent().getActivity().getDataProductVersionUuid()).isEqualTo("dpv-1");
        assertThat(event.getEventContent().getTask().getUuid()).isEqualTo("task-1");
        assertThat(event.getEventContent().getTask().getName()).isEqualTo("deploy");
    }

    /**
     * Feature: Approve an activity execution
     *
     * Scenario: Approval starts the activity and requests its first task
     *   Given a PENDING activity with two PENDING tasks
     *   When DevOps receives ACTIVITY_EXECUTION_APPROVED for it
     *   Then the activity is RUNNING with a start time
     *   And ACTIVITY_TASK_EXECUTION_REQUESTED is emitted for the task with sortOrder 0 only
     *   And the activity in that event has no tasks, and the task has no logs or results
     */
    @Test
    public void whenActivityApprovedThenRunningAndFirstTaskRequested() {
        // Given
        ActivityRes created = executeTasks("dpv-approve", "prod", Map.of());
        clearNotifications();

        // When
        postActivityApproved(created.getUuid());

        ActivityRes stored = getActivity(created.getUuid());
        // Then
        assertThat(stored.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(stored.getStartedAt()).isNotNull();
        List<EmittedEventActivityTaskExecutionRequestedRes> requests = capturedTaskRequests();
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getEventContent().getActivity().getTasks()).isNull();
        assertThat(requests.get(0).getEventContent().getTask().getSortOrder()).isEqualTo(0);
        assertThat(requests.get(0).getEventContent().getTask().getLogs()).isNull();
        assertThat(requests.get(0).getEventContent().getTask().getResults()).isNull();
    }

    /**
     * Feature: Approve an activity execution
     *
     * Scenario: A duplicate approval changes nothing
     *   Given a RUNNING activity
     *   When DevOps receives ACTIVITY_EXECUTION_APPROVED for it again
     *   Then the notification is processed successfully
     *   And the activity and its tasks are unchanged and no event is emitted
     */
    @Test
    public void whenActivityApprovedTwiceThenNotificationFailedAndUnchanged() {
        // Given
        ActivityRes created = executeTasks("dpv-approve-twice", "prod", Map.of());
        postActivityApproved(created.getUuid());
        ActivityRes running = getActivity(created.getUuid());
        clearNotifications();

        // When
        long sequenceId = postActivityApproved(created.getUuid());

        // Then
        assertProcessingSucceeded(sequenceId);
        assertThat(notifiedEvents()).isEmpty();
        ActivityRes after = getActivity(created.getUuid());
        assertThat(after.getStatus()).isEqualTo(running.getStatus());
        assertThat(after.getStartedAt()).isEqualTo(running.getStartedAt());
        assertThat(after.getTasks()).extracting(TaskRes::getStatus)
                .containsExactlyElementsOf(running.getTasks().stream().map(TaskRes::getStatus).toList());
    }

    /**
     * Feature: Execute a task on the executor
     *
     * Scenario: Two tasks succeed in order and the activity succeeds
     *   Given a RUNNING activity with two PENDING tasks on "starter"
     *   And the executor reports each run RUNNING once and then SUCCEEDED, with a log
     *   When DevOps receives ACTIVITY_TASK_EXECUTION_APPROVED for the first task, then for the second task when it is requested
     *   Then both tasks are SUCCEEDED with a providerRunId, a start and an end time, and one log each
     *   And the activity is SUCCEEDED with an end time
     *   And ACTIVITY_SUCCEEDED is emitted with the activity
     *   And the executor start requests carried only executor parameters and pipeline parameters
     */
    @Test
    public void whenAllTasksSucceedThenActivitySucceeded() {
        // Given
        stubExecutor(
                status("RUNNING"), status("SUCCEEDED"), status("RUNNING"), status("SUCCEEDED"),
                "build log"
        );
        ActivityRes created = executeTasks("dpv-succeed", "prod", Map.of("region", "eu"));
        // When
        postActivityApproved(created.getUuid());
        clearNotifications();
        TaskRes first = taskBySortOrder(created, 0);
        TaskRes second = taskBySortOrder(created, 1);

        postTaskApproved(created.getUuid(), first.getUuid());
        // Then
        assertThat(capturedTaskRequests()).singleElement().satisfies(event ->
                assertThat(event.getEventContent().getTask().getUuid()).isEqualTo(second.getUuid())
        );
        // When
        postTaskApproved(created.getUuid(), second.getUuid());

        ActivityRes stored = getActivity(created.getUuid());
        // Then
        assertThat(stored.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(stored.getFinishedAt()).isNotNull();
        assertThat(stored.getTasks()).allSatisfy(task -> {
            assertThat(task.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
            assertThat(task.getProviderRunId()).isNotBlank();
            assertThat(task.getStartedAt()).isNotNull();
            assertThat(task.getFinishedAt()).isNotNull();
            assertThat(task.getLogs()).hasSize(1);
            assertThat(task.getLogs().get(0).getContent()).isEqualTo("build log");
        });
        assertThat(notifiedEvents()).anyMatch(EmittedEventActivitySucceededRes.class::isInstance);

        assertThat(executor().starts()).hasSize(2);
        assertThat(executor().starts()).allSatisfy(command -> {
            assertThat(command.getExecutorParameters()).isNotNull();
            assertThat(command.getExecutorParameters().getDataProductRepo().getProviderType()).isEqualTo("GITHUB");
            assertThat(command.getPipelineParameters()).containsEntry("region", "eu");
        });
    }

    /**
     * Feature: Execute a task on the executor
     *
     * Scenario: The status is read until the run ends
     *   Given the executor reports RUNNING three times and then SUCCEEDED
     *   When DevOps executes the task
     *   Then the status is read four times
     *   And the task is SUCCEEDED
     */
    @Test
    public void whenRunStillRunningThenStatusPolledUntilEnd() {
        // Given
        stubExecutor(
                status("RUNNING"), status("RUNNING"), status("RUNNING"), status("SUCCEEDED"),
                "log"
        );
        ActivityRes created = executeOneTask("dpv-poll", "prod", null);
        // When
        postActivityApproved(created.getUuid());
        postTaskApproved(created.getUuid(), created.getTasks().get(0).getUuid());

        // Then
        assertThat(executor().statusReads()).isEqualTo(4);
        assertThat(getActivity(created.getUuid()).getTasks().get(0).getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
    }

    /**
     * Feature: Execute a task on the executor
     *
     * Scenario: A failed task fails the activity and cancels the remaining tasks
     *   Given a RUNNING activity with two PENDING tasks
     *   And the executor reports the first run FAILED
     *   When DevOps executes the first task
     *   Then the first task is FAILED
     *   And the second task is CANCELED with an end time and no start time
     *   And the activity is FAILED
     *   And ACTIVITY_FAILED is emitted and no task execution is requested for the second task
     */
    @Test
    public void whenTaskFailsThenActivityFailedAndRemainingCanceled() {
        // Given
        stubExecutor(status("FAILED"), "failed log");
        ActivityRes created = executeTasks("dpv-fail", "prod", Map.of());
        // When
        postActivityApproved(created.getUuid());
        clearNotifications();

        postTaskApproved(created.getUuid(), taskBySortOrder(created, 0).getUuid());

        ActivityRes stored = getActivity(created.getUuid());
        // Then
        assertThat(stored.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        TaskRes first = taskBySortOrder(stored, 0);
        TaskRes second = taskBySortOrder(stored, 1);
        assertThat(first.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(second.getStatus()).isEqualTo(ExecutionStatus.CANCELED);
        assertThat(second.getFinishedAt()).isNotNull();
        assertThat(second.getStartedAt()).isNull();
        assertThat(notifiedEvents()).anyMatch(EmittedEventActivityFailedRes.class::isInstance);
        assertThat(notifiedEvents()).noneMatch(EmittedEventActivityTaskExecutionRequestedRes.class::isInstance);
    }

    /**
     * Feature: Execute a task on the executor
     *
     * Scenario: An unknown executor status fails the task
     *   Given the executor reports the status "CANCELLED_BY_USER"
     *   When DevOps executes the task
     *   Then the task is FAILED
     */
    @Test
    public void whenExecutorStatusUnknownThenTaskFailed() {
        // Given
        stubExecutor(status("CANCELLED_BY_USER"), "log");
        ActivityRes created = executeOneTask("dpv-unknown-status", "prod", null);
        // When
        postActivityApproved(created.getUuid());
        postTaskApproved(created.getUuid(), created.getTasks().get(0).getUuid());

        // Then
        assertThat(getActivity(created.getUuid()).getTasks().get(0).getStatus()).isEqualTo(ExecutionStatus.FAILED);
    }

    /**
     * Feature: Execute a task on the executor
     *
     * Scenario: An empty log stores no log row
     *   Given the executor returns a blank log content
     *   When DevOps executes the task and the run succeeds
     *   Then the task is SUCCEEDED with no log
     */
    @Test
    public void whenLogBlankThenNoLogRow() {
        // Given
        stubExecutor(status("SUCCEEDED"), "  ");
        ActivityRes created = executeOneTask("dpv-blank-log", "prod", null);
        // When
        postActivityApproved(created.getUuid());
        postTaskApproved(created.getUuid(), created.getTasks().get(0).getUuid());

        TaskRes task = getActivity(created.getUuid()).getTasks().get(0);
        // Then
        assertThat(task.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(task.getLogs()).isNullOrEmpty();
    }

    /**
     * Feature: Execute a task on the executor
     *
     * Scenario: A duplicate task approval changes nothing
     *   Given a task that is already SUCCEEDED
     *   When DevOps receives ACTIVITY_TASK_EXECUTION_APPROVED for it again
     *   Then the notification is processed successfully
     *   And the executor is not called
     */
    @Test
    public void whenTaskApprovedTwiceThenNotificationFailedAndExecutorNotCalled() {
        // Given
        ActivityRes payload = namedActivity("dpv-task-twice", "prod");
        payload.setStatus(ExecutionStatus.RUNNING);
        TaskRes task = task("deploy", Map.of());
        task.setStatus(ExecutionStatus.SUCCEEDED);
        payload.setTasks(List.of(task));
        ActivityRes created = postActivity(payload);

        // When
        long sequenceId = postTaskApproved(created.getUuid(), created.getTasks().get(0).getUuid());

        // Then
        assertProcessingSucceeded(sequenceId);
        assertThat(executor().openedExecutors()).isEmpty();
        assertThat(getActivity(created.getUuid()).getTasks().get(0).getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
    }

    /**
     * Feature: Pipeline parameter placeholders
     *
     * Scenario: A placeholder is resolved from an earlier task result before the run starts
     *   Given a finished activity "dev" of the same data product version whose task "deploy-infrastructure" has the result {"endpoint":"https://x"}
     *   And a task with pipeline parameter infraEndpoint = "${dev.results.deploy-infrastructure.endpoint}"
     *   When DevOps executes the task
     *   Then the executor start request carries infraEndpoint = "https://x"
     *   And the stored pipeline parameter is still "${dev.results.deploy-infrastructure.endpoint}"
     */
    @Test
    public void whenPlaceholderHasResultThenResolvedAndStoredUnchanged() {
        // Given
        seedResult("dpv-placeholder", "dev", "deploy-infrastructure", "{\"endpoint\":\"https://x\"}", null);
        stubExecutor(status("SUCCEEDED"), "log");
        Map<String, String> parameters = Map.of("infraEndpoint", "${dev.results.deploy-infrastructure.endpoint}");
        ActivityRes created = executeTasks("dpv-placeholder", "prod", parameters, "deploy");
        // When
        postActivityApproved(created.getUuid());
        postTaskApproved(created.getUuid(), created.getTasks().get(0).getUuid());

        // Then
        assertThat(executor().starts()).singleElement().satisfies(command ->
                assertThat(command.getPipelineParameters()).containsEntry("infraEndpoint", "https://x")
        );
        assertThat(getActivity(created.getUuid()).getTasks().get(0).getPipelineParameters())
                .containsEntry("infraEndpoint", "${dev.results.deploy-infrastructure.endpoint}");
    }

    /**
     * Feature: Pipeline parameter placeholders
     *
     * Scenario: An unresolved placeholder is sent unchanged
     *   Given a task with pipeline parameter p = "${dev.results.missing.value}" and no such result
     *   When DevOps executes the task
     *   Then the executor start request carries p = "${dev.results.missing.value}"
     */
    @Test
    public void whenPlaceholderUnresolvedThenSentUnchanged() {
        // Given
        stubExecutor(status("SUCCEEDED"), "log");
        ActivityRes created = executeTasks(
                "dpv-unresolved",
                "prod",
                Map.of("p", "${dev.results.missing.value}"),
                "deploy"
        );
        // When
        postActivityApproved(created.getUuid());
        postTaskApproved(created.getUuid(), created.getTasks().get(0).getUuid());

        // Then
        assertThat(executor().starts()).singleElement().satisfies(command ->
                assertThat(command.getPipelineParameters()).containsEntry("p", "${dev.results.missing.value}")
        );
    }

    /**
     * Feature: Pipeline parameter placeholders
     *
     * Scenario: The latest activity per name is used
     *   Given two activities "dev" of the same data product version, the newer one with result {"v":"new"} and the older one with result {"v":"old"}
     *   When DevOps resolves "${dev.results.build.v}"
     *   Then the value is "new"
     */
    @Test
    public void whenSeveralActivitiesWithNameThenLatestUsed() {
        // Given
        ActivityRes older = seedResult("dpv-latest", "dev", "build", "{\"v\":\"old\"}", null);
        ActivityRes newer = seedResult("dpv-latest", "dev", "build", "{\"v\":\"new\"}", null);
        history.setActivityCreatedAt(older.getUuid(), "2020-01-01 00:00:00");
        history.setActivityCreatedAt(newer.getUuid(), "2024-01-01 00:00:00");
        stubExecutor(status("SUCCEEDED"), "log");
        ActivityRes created = executeTasks("dpv-latest", "prod", Map.of("p", "${dev.results.build.v}"), "deploy");
        // When
        postActivityApproved(created.getUuid());
        postTaskApproved(created.getUuid(), created.getTasks().get(0).getUuid());

        // Then
        assertThat(executor().starts()).singleElement().satisfies(command ->
                assertThat(command.getPipelineParameters()).containsEntry("p", "new")
        );
    }

    /**
     * Feature: Observer
     *
     * Scenario: An event type DevOps does not handle is acknowledged
     *   Given a notification with type "SOMETHING_ELSE"
     *   When DevOps receives it on the observer endpoint
     *   Then the response is 200
     *   And the notification is marked processed and no event is emitted
     */
    @Test
    public void whenUnknownEventTypeThenProcessed() {
        // Given
        ObjectNode content = objectMapper.createObjectNode();

        // When
        long sequenceId = postNotification("SOMETHING_ELSE", content);

        // Then
        assertProcessingSucceeded(sequenceId);
        assertThat(notifiedEvents()).isEmpty();
    }

    private void stubExecutor(String logContent, ExecutorTaskStatusRes... statuses) {
        executor().statuses(statuses).logs(logContent);
    }

    private void stubExecutor(ExecutorTaskStatusRes first, String logContent) {
        executor().statuses(first).logs(logContent);
    }

    private void stubExecutor(ExecutorTaskStatusRes first, ExecutorTaskStatusRes second,
                              ExecutorTaskStatusRes third, ExecutorTaskStatusRes fourth,
                              String logContent) {
        executor().statuses(first, second, third, fourth).logs(logContent);
    }

    private static ExecutorTaskStatusRes status(String value) {
        ExecutorTaskStatusRes status = new ExecutorTaskStatusRes();
        status.setStatus(value);
        return status;
    }

    private ActivityRes executeOneTask(String dataProductVersionUuid, String name, String secret) {
        ActivityRes created = executeTasks(dataProductVersionUuid, name, Map.of(), "deploy", secret);
        assertThat(created.getTasks()).hasSize(1);
        return created;
    }

    private ActivityRes executeTasks(String dataProductVersionUuid, String name, Map<String, String> pipelineParameters) {
        return executeTasks(dataProductVersionUuid, name, pipelineParameters, null, null);
    }

    private ActivityRes executeTasks(String dataProductVersionUuid, String name, Map<String, String> pipelineParameters, String onlyTaskName) {
        return executeTasks(dataProductVersionUuid, name, pipelineParameters, onlyTaskName, null);
    }

    private ActivityRes executeTasks(String dataProductVersionUuid, String name, Map<String, String> pipelineParameters,
                                     String onlyTaskName, String secret) {
        ActivityRes activity = namedActivity(dataProductVersionUuid, name);
        if (onlyTaskName == null) {
            activity.setTasks(List.of(task("deploy", pipelineParameters), task("publish", pipelineParameters)));
        } else {
            activity.setTasks(List.of(task(onlyTaskName, pipelineParameters)));
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (secret != null) {
            headers.add("x-odm-starter-executor-secret-token", secret);
        }
        ActivityExecuteCommandRes command = new ActivityExecuteCommandRes();
        command.setActivity(activity);
        ResponseEntity<ActivityExecuteResultRes> response = rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES_EXECUTE),
                new HttpEntity<>(command, headers),
                ActivityExecuteResultRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().getActivity();
    }

    private ActivityRes seedResult(String dataProductVersionUuid, String activityName, String taskName, String content, Date generatedAt) {
        ActivityRes activity = namedActivity(dataProductVersionUuid, activityName);
        activity.setStatus(ExecutionStatus.SUCCEEDED);
        TaskRes task = new TaskRes();
        task.setName(taskName);
        task.setStatus(ExecutionStatus.SUCCEEDED);
        TaskResultRes result = new TaskResultRes();
        result.setContent(content);
        result.setGeneratedAt(generatedAt);
        task.setResults(List.of(result));
        activity.setTasks(List.of(task));
        return postActivity(activity);
    }

    private ActivityRes postActivity(ActivityRes activity) {
        ResponseEntity<ActivityRes> response = rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES),
                new HttpEntity<>(activity),
                ActivityRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private long postActivityApproved(String activityUuid) {
        ObjectNode content = objectMapper.createObjectNode();
        content.putObject("activity").put("uuid", activityUuid);
        return postNotification("ACTIVITY_EXECUTION_APPROVED", content);
    }

    private long postTaskApproved(String activityUuid, String taskUuid) {
        ObjectNode content = objectMapper.createObjectNode();
        content.putObject("activity").put("uuid", activityUuid);
        content.putObject("task").put("uuid", taskUuid);
        return postNotification("ACTIVITY_TASK_EXECUTION_APPROVED", content);
    }

    private long postNotification(String type, ObjectNode content) {
        long sequenceId = sequence++;
        NotificationDispatchRes notification = new NotificationDispatchRes();
        notification.setSequenceId(sequenceId);
        NotificationDispatchRes.NotificationDispatchEventRes event = new NotificationDispatchRes.NotificationDispatchEventRes();
        event.setType(type);
        event.setResourceType("ACTIVITY");
        event.setResourceIdentifier("ignored");
        event.setEventTypeVersion("V2.0.0");
        event.setEventContent(content);
        notification.setEvent(event);
        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.OBSERVER_NOTIFICATIONS),
                notification,
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return sequenceId;
    }

    private ActivityRes getActivity(String uuid) {
        ResponseEntity<ActivityRes> response = rest.getForEntity(
                apiUrl(RoutesV2.ACTIVITIES, "/" + uuid),
                ActivityRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<EmittedEventActivityTaskExecutionRequestedRes> capturedTaskRequests() {
        return notifiedEvents().stream()
                .filter(EmittedEventActivityTaskExecutionRequestedRes.class::isInstance)
                .map(EmittedEventActivityTaskExecutionRequestedRes.class::cast)
                .toList();
    }

    private static TaskRes taskBySortOrder(ActivityRes activity, int sortOrder) {
        return activity.getTasks().stream()
                .filter(task -> task.getSortOrder() != null && task.getSortOrder() == sortOrder)
                .findFirst()
                .orElseThrow();
    }

    private static ActivityRes namedActivity(String dataProductVersionUuid, String name) {
        ActivityRes activity = new ActivityRes();
        activity.setDataProductVersionUuid(dataProductVersionUuid);
        activity.setName(name);
        activity.setSortOrder(1);
        return activity;
    }

    private static TaskRes task(String name, Map<String, String> pipelineParameters) {
        TaskRes task = new TaskRes();
        task.setName(name);
        task.setExecutorName("starter");
        task.setExecutorParameters(executorParameters());
        if (!pipelineParameters.isEmpty()) {
            task.setPipelineParameters(new LinkedHashMap<>(pipelineParameters));
        }
        return task;
    }

    private static ExecutorParametersRes executorParameters() {
        ExecutorParametersRes parameters = new ExecutorParametersRes();
        parameters.setPipelineIdentifier("deploy");
        DataProductRepoRes repository = new DataProductRepoRes();
        repository.setProviderType("GITHUB");
        repository.setName("orders");
        parameters.setDataProductRepo(repository);
        GitRefRes ref = new GitRefRes();
        ref.setName("v1.2.0");
        ref.setType(GitRefType.TAG);
        parameters.setRef(ref);
        return parameters;
    }
}
