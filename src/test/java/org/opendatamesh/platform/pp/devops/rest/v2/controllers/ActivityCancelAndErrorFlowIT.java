package org.opendatamesh.platform.pp.devops.rest.v2.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.GitRefType;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStatusRes;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientException;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;
import org.opendatamesh.platform.pp.devops.rest.v2.ActivityHistoryFixture;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cancel, rejection, executor failure, and placeholder scenarios.
 * Scenarios trace to {@code spdd/prompt/BDMD-5440-202610021738-[Feat]-service-full-control-cancel-error-paths.md}.
 */
public class ActivityCancelAndErrorFlowIT extends DevOpsApplicationIT {

    private static final String RUN_ID = "run-1";



    @Autowired
    private ExecutorServicesProperties executorServicesProperties;

    @Autowired
    private ActivityHistoryFixture history;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.jpa.properties.hibernate.default_schema}")
    private String schema;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private long sequence = 1L;

    @BeforeEach
    void resetState() {
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
        // Given
        ActivityRes created = succeededActivity("dpv-already-succeeded", "prod");
        ActivityRes before = getActivity(created.getUuid());

        // When
        ResponseEntity<ErrorRes> response = postCancelError(created.getUuid());

        // Then
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
        // Given
        ActivityRes created = executeTasks("dpv-cancel-pending", "prod", Map.of());
        clearNotifications();

        // When
        ActivityRes canceled = postCancel(created.getUuid());

        // Then
        assertThat(canceled.getStatus()).isEqualTo(ExecutionStatus.CANCELED);
        assertThat(canceled.getFinishedAt()).isNotNull();
        assertThat(canceled.getStartedAt()).isNull();
        assertThat(canceled.getTasks()).allSatisfy(task -> {
            assertThat(task.getStatus()).isEqualTo(ExecutionStatus.CANCELED);
            assertThat(task.getFinishedAt()).isNotNull();
            assertThat(task.getStartedAt()).isNull();
        });
        assertThat(executor().openedExecutors()).isEmpty();
        assertThat(capturedEvents()).anyMatch(EmittedEventActivityCanceledRes.class::isInstance);
        ActivityRes stored = getActivity(created.getUuid());
        assertThat(stored.getStatus()).isEqualTo(ExecutionStatus.CANCELED);
    }

    /**
     * Feature: Cancel an activity
     *
     * Scenario: A cancel that conflicts with another update returns 409
     *   Given another transaction has updated the activity and has not committed
     *   When the user cancels the activity
     *   Then the response is 409
     *   And the activity and its tasks stay PENDING
     *   And a later cancel, after the other transaction ends, cancels the activity
     */
    @Test
    public void whenCancelConflictsThenResponseIs409() throws Exception {
        // Given
        ActivityRes created = executeTasks("dpv-cancel-conflict", "prod", Map.of());
        jdbcTemplate.execute("ALTER ROLE test SET lock_timeout = '1s'");
        evictPooledConnections();
        Connection holder = dataSource.getConnection();
        try {
            holder.setAutoCommit(false);
            try (Statement statement = holder.createStatement()) {
                statement.execute("SET lock_timeout TO '0'");
            }
            try (PreparedStatement updateActivity = holder.prepareStatement(
                    "UPDATE " + schema + ".activities SET updated_at = updated_at WHERE uuid = ?")) {
                updateActivity.setString(1, created.getUuid());
                assertThat(updateActivity.executeUpdate()).isEqualTo(1);
            }
            try (PreparedStatement updateTasks = holder.prepareStatement(
                    "UPDATE " + schema + ".activities_tasks SET updated_at = updated_at WHERE activity_uuid = ?")) {
                updateTasks.setString(1, created.getUuid());
                assertThat(updateTasks.executeUpdate()).isEqualTo(created.getTasks().size());
            }

            // When
            ResponseEntity<ErrorRes> response = postCancelError(created.getUuid());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().getMessage())
                    .isEqualTo("The resource is unavailable at the moment please retry");
            ActivityRes stored = getActivity(created.getUuid());
            assertThat(stored.getStatus()).isEqualTo(ExecutionStatus.PENDING);
            assertThat(stored.getTasks()).allSatisfy(task -> assertThat(task.getStatus()).isEqualTo(ExecutionStatus.PENDING));
        } finally {
            holder.rollback();
            holder.close();
            jdbcTemplate.execute("ALTER ROLE test RESET lock_timeout");
            evictPooledConnections();
        }

        // When
        ActivityRes canceled = postCancel(created.getUuid());

        // Then
        assertThat(canceled.getStatus()).isEqualTo(ExecutionStatus.CANCELED);
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
        // Given
        ActivityRes created = executeTasks("dpv-reject-activity", "prod", Map.of());
        clearNotifications();

        // When
        postActivityRejected(created.getUuid());

        ActivityRes stored = getActivity(created.getUuid());
        // Then
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
        // Given
        ActivityRes created = executeTasks("dpv-reject-task", "prod", Map.of());
        // When
        postActivityApproved(created.getUuid());
        clearNotifications();

        postTaskRejected(created.getUuid(), taskBySortOrder(created, 0).getUuid());

        ActivityRes stored = getActivity(created.getUuid());
        // Then
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
        // Given
        ActivityRes created = executeTasks("dpv-reject-after-cancel", "prod", Map.of());
        postCancel(created.getUuid());
        clearNotifications();

        // When
        long sequenceId = postActivityRejected(created.getUuid());

        // Then
        assertProcessingSucceeded(sequenceId);
        assertThat(notifiedEvents()).isEmpty();
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
        // Given
        stubExecutor(status("SUCCEEDED"), "log");
        executor().startThrows(new RuntimeException("start failed"));
        ActivityRes created = executeOneTask("dpv-start-throws", "prod");
        // When
        postActivityApproved(created.getUuid());

        postTaskApproved(created.getUuid(), created.getTasks().get(0).getUuid());

        ActivityRes stored = getActivity(created.getUuid());
        // Then
        assertThat(stored.getTasks().get(0).getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(stored.getTasks().get(0).getStatus()).isNotEqualTo(ExecutionStatus.RUNNING);
        assertThat(stored.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(executor().statusReads()).isZero();
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
        // Given
        stubExecutor(status("CANCELED"), "log");
        executor().logsThrow(new ClientException(500, "log body must not be required"));
        ActivityRes created = executeOneTask("dpv-log-throws", "prod");
        // When
        postActivityApproved(created.getUuid());

        postTaskApproved(created.getUuid(), created.getTasks().get(0).getUuid());

        ActivityRes stored = getActivity(created.getUuid());
        // Then
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
        // Given
        Duration previousInterval = executorServicesProperties.getPollingInterval();
        Duration previousTtl = executorServicesProperties.getTtl();
        executorServicesProperties.setPollingInterval(Duration.ofMillis(1));
        executorServicesProperties.setSecretsTtl(Duration.ofMillis(3));
        // When
        try {
            stubExecutor(status("RUNNING"), "log");
            ActivityRes created = executeOneTask("dpv-poll-cap", "prod");
            postActivityApproved(created.getUuid());

            postTaskApproved(created.getUuid(), created.getTasks().get(0).getUuid());

            assertThat(executor().statusReads()).isEqualTo(3);
            ActivityRes stored = getActivity(created.getUuid());
            assertThat(stored.getTasks().get(0).getStatus()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(stored.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        } finally {
            executorServicesProperties.setPollingInterval(previousInterval);
            executorServicesProperties.setSecretsTtl(previousTtl);
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
        // Given
        seedSucceededAndLaterCanceled("dpv-ignore-canceled", "dev", "deploy-infrastructure");
        stubExecutor(status("SUCCEEDED"), "log");
        ActivityRes created = executeTasks(
                "dpv-ignore-canceled",
                "prod",
                Map.of("infraEndpoint", "${dev.results.deploy-infrastructure.endpoint}"),
                "deploy"
        );
        // When
        postActivityApproved(created.getUuid());
        postTaskApproved(created.getUuid(), created.getTasks().get(0).getUuid());

        // Then
        assertThat(executor().starts()).singleElement().satisfies(command ->
                assertThat(command.getPipelineParameters()).containsEntry("infraEndpoint", "https://from-succeeded")
        );
    }

    private void stubExecutor(ExecutorTaskStatusRes status, String logContent) {
        executor().statuses(status).logs(logContent).startRunId(RUN_ID);
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

    private void evictPooledConnections() {
        ((HikariDataSource) dataSource).getHikariPoolMXBean().softEvictConnections();
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
        history.setTaskCreatedAt(succeededUuid, "2020-01-01 00:00:00");
        history.setTaskCreatedAt(canceledUuid, "2024-01-01 00:00:00");
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
        return notifiedEvents();
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
