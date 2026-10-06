package org.opendatamesh.platform.pp.devops.rest.v2.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.GitRefType;
import org.opendatamesh.platform.pp.devops.activity.repositories.ActivitiesRepository;
import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClient;
import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClientFactory;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskLogsRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartResultRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStatusRes;
import org.opendatamesh.platform.pp.devops.client.notification.NotificationClient;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientException;
import org.opendatamesh.platform.pp.devops.executor.ExecutorPollingProperties;
import org.opendatamesh.platform.pp.devops.executor.ExecutorSecretsProperties;
import org.opendatamesh.platform.pp.devops.rest.v2.DevOpsApplicationIT;
import org.opendatamesh.platform.pp.devops.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.ErrorRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ExecutorParametersRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.GitRefRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.DataProductRepoRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskResultRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.emitted.EmittedEventActivityCanceledRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.emitted.EmittedEventActivityFailedRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.cancel.ActivityCancelCommandRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.cancel.ActivityCancelResultRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.execute.ActivityExecuteCommandRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.execute.ActivityExecuteResultRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.notification.NotificationDispatchRes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cancel, rejection, executor failure, and placeholder scenarios.
 * Scenarios trace to {@code spdd/prompt/BDMD-5440-202610021738-[Feat]-service-full-control-cancel-error-paths.md}.
 */
public class ActivityCancelAndErrorFlowIT extends DevOpsApplicationIT {

    private static final String RUN_ID = "run-1";

    @Autowired
    private ActivitiesRepository activitiesRepository;

    @Autowired
    private NotificationClient notificationClient;

    @Autowired
    private ExecutorClientFactory executorClientFactory;

    @Autowired
    private ExecutorPollingProperties pollingProperties;

    @Autowired
    private ExecutorSecretsProperties secretsProperties;

    @MockitoSpyBean
    private ActivityService activityService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.jpa.properties.hibernate.default_schema}")
    private String schema;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private long sequence = 1L;

    @BeforeEach
    void resetState() {
        activitiesRepository.deleteAll();
        reset(notificationClient, executorClientFactory);
        sequence = 1L;
    }

    /**
     * Feature: Cancel an activity
     *
     * Scenario: Cancel is refused when the activity has already succeeded
     *   Given an activity whose tasks have succeeded
     *   When the user cancels that activity
     *   Then the response is 400 and says the activity has already terminated with status SUCCEEDED
     *   And the activity and its tasks are unchanged
     */
    @Test
    public void whenActivityAlreadySucceededThenCancelReturns400() {
        ActivityRes created = succeededActivity("dpv-already-succeeded", "prod");
        ActivityRes before = getActivity(created.getUuid());

        ResponseEntity<ErrorRes> response = postCancelError(created.getUuid());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage())
                .isEqualTo("Activity " + created.getUuid() + " has already terminated with status SUCCEEDED");
        ActivityRes after = getActivity(created.getUuid());
        assertThat(after.getStatus()).isEqualTo(before.getStatus());
        assertThat(after.getStartedAt()).isEqualTo(before.getStartedAt());
        assertThat(after.getFinishedAt()).isEqualTo(before.getFinishedAt());
        assertThat(after.getTasks()).extracting(TaskRes::getStatus)
                .containsExactlyElementsOf(before.getTasks().stream().map(TaskRes::getStatus).toList());
        assertThat(after.getTasks()).extracting(TaskRes::getFinishedAt)
                .containsExactlyElementsOf(before.getTasks().stream().map(TaskRes::getFinishedAt).toList());
    }

    /**
     * Feature: Cancel an activity
     *
     * Scenario: Cancel closes an activity that is still waiting for approval
     *   Given an activity and its tasks are PENDING and execution has been requested
     *   When the user cancels that activity
     *   Then every task is CANCELED and the activity is CANCELED
     *   And the activity has a finish time and no start time
     *   And the executor cancel endpoint was not called
     *   And ACTIVITY_CANCELED was emitted
     *   And the response is 200 with that activity
     */
    @Test
    public void whenActivityPendingThenCancelClosesIt() {
        ActivityRes created = executeTasks("dpv-cancel-pending", "prod", Map.of());
        clearInvocations(notificationClient);

        ActivityRes canceled = postCancel(created.getUuid());

        assertThat(canceled.getStatus()).isEqualTo(ExecutionStatus.CANCELED);
        assertThat(canceled.getFinishedAt()).isNotNull();
        assertThat(canceled.getStartedAt()).isNull();
        assertThat(canceled.getTasks()).allSatisfy(task -> {
            assertThat(task.getStatus()).isEqualTo(ExecutionStatus.CANCELED);
            assertThat(task.getFinishedAt()).isNotNull();
            assertThat(task.getStartedAt()).isNull();
        });
        verify(executorClientFactory, never()).getExecutorClient(anyString(), anyString());
        assertThat(capturedEvents()).anyMatch(EmittedEventActivityCanceledRes.class::isInstance);
        ActivityRes stored = getActivity(created.getUuid());
        assertThat(stored.getStatus()).isEqualTo(ExecutionStatus.CANCELED);
    }

    /**
     * Feature: Cancel an activity
     *
     * Scenario: A cancel that conflicts with another update returns 409
     *   Given a cancel transaction conflicts with a concurrent update of the same activity
     *   When the user cancels the activity
     *   Then the response is 409
     *   And DevOps does not retry the cancel
     */
    @Test
    public void whenCancelConflictsThenResponseIs409() {
        ActivityRes created = executeTasks("dpv-cancel-conflict", "prod", Map.of());
        clearInvocations(activityService);
        doThrow(new CannotAcquireLockException("conflict"))
                .when(activityService)
                .overwrite(anyString(), any(Activity.class));
        try {
            ResponseEntity<ErrorRes> response = postCancelError(created.getUuid());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            verify(activityService, times(1)).overwrite(anyString(), any(Activity.class));
            ActivityRes stored = getActivity(created.getUuid());
            assertThat(stored.getStatus()).isEqualTo(ExecutionStatus.PENDING);
            assertThat(stored.getTasks()).allSatisfy(task -> assertThat(task.getStatus()).isEqualTo(ExecutionStatus.PENDING));
        } finally {
            doCallRealMethod().when(activityService).overwrite(anyString(), any(Activity.class));
        }
    }

    /**
     * Feature: Reject an execution
     *
     * Scenario: An activity execution rejection fails the activity
     *   Given an activity and its tasks are PENDING
     *   When an activity execution rejected event arrives for it
     *   Then every task is CANCELED and the activity is FAILED
     *   And ACTIVITY_FAILED was emitted
     *   And ACTIVITY_CANCELED was not emitted
     */
    @Test
    public void whenActivityExecutionRejectedThenActivityFailed() {
        ActivityRes created = executeTasks("dpv-reject-activity", "prod", Map.of());
        clearInvocations(notificationClient);

        postActivityRejected(created.getUuid());

        ActivityRes stored = getActivity(created.getUuid());
        assertThat(stored.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(stored.getStartedAt()).isNull();
        assertThat(stored.getFinishedAt()).isNotNull();
        assertThat(stored.getTasks()).allSatisfy(task -> {
            assertThat(task.getStatus()).isEqualTo(ExecutionStatus.CANCELED);
            assertThat(task.getStartedAt()).isNull();
        });
        List<Object> events = capturedEvents();
        assertThat(events).anyMatch(EmittedEventActivityFailedRes.class::isInstance);
        assertThat(events).noneMatch(EmittedEventActivityCanceledRes.class::isInstance);
    }

    /**
     * Feature: Reject an execution
     *
     * Scenario: A task execution rejection fails that task and the activity
     *   Given the activity is RUNNING and its tasks are PENDING
     *   When a task execution rejected event arrives for the first task
     *   Then that task is FAILED
     *   And every other task is CANCELED
     *   And the activity is FAILED
     *   And ACTIVITY_FAILED was emitted
     */
    @Test
    public void whenTaskExecutionRejectedThenActivityFailed() {
        ActivityRes created = executeTasks("dpv-reject-task", "prod", Map.of());
        postActivityApproved(created.getUuid());
        clearInvocations(notificationClient);

        postTaskRejected(created.getUuid(), taskBySortOrder(created, 0).getUuid());

        ActivityRes stored = getActivity(created.getUuid());
        assertThat(taskBySortOrder(stored, 0).getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(taskBySortOrder(stored, 0).getStartedAt()).isNull();
        assertThat(taskBySortOrder(stored, 1).getStatus()).isEqualTo(ExecutionStatus.CANCELED);
        assertThat(stored.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(capturedEvents()).anyMatch(EmittedEventActivityFailedRes.class::isInstance);
        assertThat(capturedEvents()).noneMatch(EmittedEventActivityCanceledRes.class::isInstance);
    }

    /**
     * Feature: Reject an execution
     *
     * Scenario: A rejection after the activity was canceled changes nothing
     *   Given the activity is CANCELED
     *   When an activity execution rejected event arrives for it
     *   Then the activity stays CANCELED
     *   And the notification is processed successfully
     */
    @Test
    public void whenRejectedAfterCancelThenActivityUnchanged() {
        ActivityRes created = executeTasks("dpv-reject-after-cancel", "prod", Map.of());
        postCancel(created.getUuid());
        clearInvocations(notificationClient);

        long sequenceId = postActivityRejected(created.getUuid());

        verify(notificationClient).processingSuccess(sequenceId);
        verify(notificationClient, never()).notifyEvent(any());
        assertThat(getActivity(created.getUuid()).getStatus()).isEqualTo(ExecutionStatus.CANCELED);
    }

    /**
     * Feature: Executor failures
     *
     * Scenario: A start call that throws fails the task and the activity
     *   Given a task has been approved and the executor start call throws
     *   When the task execution runs
     *   Then the task is FAILED and the activity is FAILED
     *   And the task does not stay RUNNING
     */
    @Test
    public void whenStartThrowsThenActivityFailed() {
        ExecutorClient client = stubExecutor(status("SUCCEEDED"), "log");
        when(client.startTask(any())).thenThrow(new RuntimeException("start failed"));
        ActivityRes created = executeOneTask("dpv-start-throws", "prod");
        postActivityApproved(created.getUuid());

        postTaskApproved(created.getUuid(), created.getTasks().get(0).getUuid());

        ActivityRes stored = getActivity(created.getUuid());
        assertThat(stored.getTasks().get(0).getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(stored.getTasks().get(0).getStatus()).isNotEqualTo(ExecutionStatus.RUNNING);
        assertThat(stored.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        verify(client, never()).getTaskStatus(anyString());
    }

    /**
     * Feature: Executor failures
     *
     * Scenario: A log read that throws still records the status the poll already decided
     *   Given the executor reports the run CANCELED and the log read throws
     *   When the task execution runs
     *   Then the task is CANCELED and the activity is CANCELED
     *   And the task does not stay RUNNING
     */
    @Test
    public void whenLogReadThrowsThenCanceledOutcomeIsRecorded() {
        ExecutorClient client = stubExecutor(status("CANCELED"), "log");
        when(client.getTaskLogs(anyString())).thenThrow(new ClientException(500, "log body must not be required"));
        ActivityRes created = executeOneTask("dpv-log-throws", "prod");
        postActivityApproved(created.getUuid());

        postTaskApproved(created.getUuid(), created.getTasks().get(0).getUuid());

        ActivityRes stored = getActivity(created.getUuid());
        assertThat(stored.getTasks().get(0).getStatus()).isEqualTo(ExecutionStatus.CANCELED);
        assertThat(stored.getStatus()).isEqualTo(ExecutionStatus.CANCELED);
    }

    /**
     * Feature: Executor failures
     *
     * Scenario: The status poll stops at the secrets lifetime and fails the task
     *   Given the poll allows 3 status reads and every read is RUNNING
     *   When the task execution runs
     *   Then the task is FAILED and the activity is FAILED
     */
    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    public void whenStatusPollReachesCapThenActivityFailed() {
        Duration previousInterval = pollingProperties.getInterval();
        Duration previousTtl = secretsProperties.getTtl();
        pollingProperties.setInterval(Duration.ofMillis(1));
        secretsProperties.setTtl(Duration.ofMillis(3));
        try {
            ExecutorClient client = stubExecutor(status("RUNNING"), "log");
            ActivityRes created = executeOneTask("dpv-poll-cap", "prod");
            postActivityApproved(created.getUuid());

            postTaskApproved(created.getUuid(), created.getTasks().get(0).getUuid());

            verify(client, times(3)).getTaskStatus(anyString());
            ActivityRes stored = getActivity(created.getUuid());
            assertThat(stored.getTasks().get(0).getStatus()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(stored.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        } finally {
            pollingProperties.setInterval(previousInterval);
            secretsProperties.setTtl(previousTtl);
        }
    }

    /**
     * Feature: Placeholder results
     *
     * Scenario: Results of a task that is not succeeded are not substituted
     *   Given a succeeded task and a later canceled task of the same name hold different results
     *   When the next task starts and its pipeline parameter names the result path
     *   Then the start request carries the succeeded task's value
     */
    @Test
    public void whenCanceledTaskHasResultsThenSucceededValueIsUsed() {
        seedSucceededAndLaterCanceled("dpv-ignore-canceled", "dev", "deploy-infrastructure");
        ExecutorClient client = stubExecutor(status("SUCCEEDED"), "log");
        ActivityRes created = executeTasks(
                "dpv-ignore-canceled",
                "prod",
                Map.of("infraEndpoint", "${dev.results.deploy-infrastructure.endpoint}"),
                "deploy"
        );
        postActivityApproved(created.getUuid());
        postTaskApproved(created.getUuid(), created.getTasks().get(0).getUuid());

        ArgumentCaptor<ExecutorTaskStartCommandRes> starts = ArgumentCaptor.forClass(ExecutorTaskStartCommandRes.class);
        verify(client).startTask(starts.capture());
        assertThat(starts.getValue().getPipelineParameters()).containsEntry("infraEndpoint", "https://from-succeeded");
    }

    private ExecutorClient stubExecutor(ExecutorTaskStatusRes status, String logContent) {
        ExecutorClient client = mock(ExecutorClient.class);
        when(executorClientFactory.getExecutorClient(eq("starter"), anyString())).thenReturn(client);
        when(client.startTask(any())).thenAnswer(invocation -> {
            ExecutorTaskStartResultRes result = new ExecutorTaskStartResultRes();
            result.setProviderRunId(RUN_ID);
            return result;
        });
        when(client.getTaskStatus(anyString())).thenReturn(status);
        when(client.getTaskLogs(anyString())).thenReturn(log(logContent));
        return client;
    }

    private static ExecutorTaskLogsRes log(String content) {
        ExecutorTaskLogsRes logs = new ExecutorTaskLogsRes();
        logs.setContent(content);
        logs.setGeneratedAt(new Date(1_700_000_000_000L));
        return logs;
    }

    private static ExecutorTaskStatusRes status(String value) {
        ExecutorTaskStatusRes status = new ExecutorTaskStatusRes();
        status.setStatus(value);
        return status;
    }

    private ActivityRes postCancel(String activityUuid) {
        ResponseEntity<ActivityCancelResultRes> response = rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES_CANCEL),
                new HttpEntity<>(cancelCommand(activityUuid)),
                ActivityCancelResultRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getActivity().getStatus()).isIn(
                ExecutionStatus.SUCCEEDED,
                ExecutionStatus.FAILED,
                ExecutionStatus.CANCELED
        );
        return response.getBody().getActivity();
    }

    private ResponseEntity<ErrorRes> postCancelError(String activityUuid) {
        return rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES_CANCEL),
                new HttpEntity<>(cancelCommand(activityUuid)),
                ErrorRes.class
        );
    }

    private static ActivityCancelCommandRes cancelCommand(String activityUuid) {
        ActivityRes activity = new ActivityRes();
        activity.setUuid(activityUuid);
        ActivityCancelCommandRes command = new ActivityCancelCommandRes();
        command.setActivity(activity);
        return command;
    }

    private ActivityRes executeOneTask(String dataProductVersionUuid, String name) {
        ActivityRes created = executeTasks(dataProductVersionUuid, name, Map.of(), "deploy");
        assertThat(created.getTasks()).hasSize(1);
        return created;
    }

    private ActivityRes executeTasks(String dataProductVersionUuid, String name, Map<String, String> pipelineParameters) {
        return executeTasks(dataProductVersionUuid, name, pipelineParameters, null);
    }

    private ActivityRes executeTasks(String dataProductVersionUuid, String name, Map<String, String> pipelineParameters, String onlyTaskName) {
        ActivityRes activity = namedActivity(dataProductVersionUuid, name);
        if (onlyTaskName == null) {
            activity.setTasks(List.of(task("deploy", pipelineParameters), task("publish", pipelineParameters)));
        } else {
            activity.setTasks(List.of(task(onlyTaskName, pipelineParameters)));
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
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

    private ActivityRes succeededActivity(String dataProductVersionUuid, String name) {
        ActivityRes activity = namedActivity(dataProductVersionUuid, name);
        activity.setStatus(ExecutionStatus.SUCCEEDED);
        activity.setStartedAt(new Date(1_700_000_000_000L));
        activity.setFinishedAt(new Date(1_700_000_100_000L));
        TaskRes task = task("deploy", Map.of());
        task.setStatus(ExecutionStatus.SUCCEEDED);
        task.setStartedAt(new Date(1_700_000_000_000L));
        task.setFinishedAt(new Date(1_700_000_100_000L));
        activity.setTasks(List.of(task));
        return postActivity(activity);
    }

    private void seedSucceededAndLaterCanceled(String dataProductVersionUuid, String activityName, String taskName) {
        ActivityRes activity = namedActivity(dataProductVersionUuid, activityName);
        activity.setStatus(ExecutionStatus.SUCCEEDED);
        activity.setTasks(List.of(
                resultTask(taskName, ExecutionStatus.SUCCEEDED, "{\"endpoint\":\"https://from-succeeded\"}"),
                resultTask(taskName, ExecutionStatus.CANCELED, "{\"endpoint\":\"https://from-canceled\"}")
        ));
        ActivityRes created = postActivity(activity);
        String succeededUuid = created.getTasks().stream()
                .filter(task -> task.getStatus() == ExecutionStatus.SUCCEEDED)
                .findFirst()
                .orElseThrow()
                .getUuid();
        String canceledUuid = created.getTasks().stream()
                .filter(task -> task.getStatus() == ExecutionStatus.CANCELED)
                .findFirst()
                .orElseThrow()
                .getUuid();
        setTaskCreatedAt(succeededUuid, "2020-01-01 00:00:00");
        setTaskCreatedAt(canceledUuid, "2024-01-01 00:00:00");
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

    private void setTaskCreatedAt(String uuid, String timestamp) {
        jdbcTemplate.update(
                "update " + schema + ".activities_tasks set created_at = ? where uuid = ?",
                Timestamp.valueOf(timestamp),
                uuid
        );
    }

    private long postActivityApproved(String activityUuid) {
        ObjectNode content = objectMapper.createObjectNode();
        content.putObject("activity").put("uuid", activityUuid);
        return postNotification("ACTIVITY_EXECUTION_APPROVED", content);
    }

    private long postActivityRejected(String activityUuid) {
        ObjectNode content = objectMapper.createObjectNode();
        content.putObject("activity").put("uuid", activityUuid);
        return postNotification("ACTIVITY_EXECUTION_REJECTED", content);
    }

    private long postTaskApproved(String activityUuid, String taskUuid) {
        ObjectNode content = objectMapper.createObjectNode();
        content.putObject("activity").put("uuid", activityUuid);
        content.putObject("task").put("uuid", taskUuid);
        return postNotification("ACTIVITY_TASK_EXECUTION_APPROVED", content);
    }

    private long postTaskRejected(String activityUuid, String taskUuid) {
        ObjectNode content = objectMapper.createObjectNode();
        content.putObject("activity").put("uuid", activityUuid);
        content.putObject("task").put("uuid", taskUuid);
        return postNotification("ACTIVITY_TASK_EXECUTION_REJECTED", content);
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

    private List<Object> capturedEvents() {
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(notificationClient, atLeastOnce()).notifyEvent(events.capture());
        return events.getAllValues();
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

    private static TaskRes resultTask(String name, ExecutionStatus status, String content) {
        TaskRes task = new TaskRes();
        task.setName(name);
        task.setStatus(status);
        TaskResultRes result = new TaskResultRes();
        result.setContent(content);
        task.setResults(List.of(result));
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
