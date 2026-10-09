package org.opendatamesh.platform.pp.devops.rest.v2.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.GitRefType;
import org.opendatamesh.platform.pp.devops.activity.repositories.ActivitiesRepository;
import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClient;
import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClientFactory;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartResultRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStatusRes;
import org.opendatamesh.platform.pp.devops.client.notification.NotificationClient;
import org.opendatamesh.platform.pp.devops.rest.v2.DevOpsApplicationIT;
import org.opendatamesh.platform.pp.devops.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.ErrorRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.DataProductRepoRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ExecutorParametersRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.GitRefRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskLogRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskResultRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.emitted.EmittedEventActivitySucceededRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.emitted.EmittedEventActivityTaskExecutionRequestedRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.cancel.ActivityCancelCommandRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.cancel.ActivityCancelResultRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.execute.ActivityExecuteCommandRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.execute.ActivityExecuteResultRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.task.ActivityTaskCommandResultRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.task.ActivityTaskExecutionRequestCommandRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.task.ActivityTaskLogsCommandRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.task.ActivityTaskResultsCommandRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.task.ActivityTaskStatusCommandRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.notification.NotificationDispatchRes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Instrumented path, mixed hand-off, shared results, and cancel.
 * Scenarios trace to {@code spdd/prompt/BDMD-5442-202610081454-[Feat]-service-instrumented-path.md}.
 */
public class ActivityInstrumentedFlowIT extends DevOpsApplicationIT {

    private static final String NOT_RUNNING = " is not RUNNING";

    @Autowired
    private ActivitiesRepository activitiesRepository;

    @Autowired
    private NotificationClient notificationClient;

    @Autowired
    private ExecutorClientFactory executorClientFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private long sequence = 1L;

    @BeforeEach
    void resetState() {
        activitiesRepository.deleteAll();
        reset(notificationClient, executorClientFactory);
        sequence = 1L;
    }

    /**
     * Feature: Approve an instrumented activity
     *
     * Scenario: Activity approval does not request an instrumented task
     *   Given an activity whose only task is instrumented and PENDING
     *   When activity execution is approved
     *   Then the activity is RUNNING
     *   And the task is PENDING
     *   And no ACTIVITY_TASK_EXECUTION_REQUESTED event is emitted
     */
    @Test
    public void whenActivityApprovedThenInstrumentedTaskStaysPending() {
        ActivityRes created = executeNamed("dpv-approve-cli", "deploy");
        clearInvocations(notificationClient);

        postActivityApproved(created.getUuid());

        ActivityRes stored = getActivity(created.getUuid());
        assertThat(stored.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(taskByName(stored, "deploy").getStatus()).isEqualTo(ExecutionStatus.PENDING);
        assertNoTaskExecutionRequested();
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Approve an instrumented activity
     *
     * Scenario: Activity approval does not request a later full-control task
     *   Given the activity tasks are instrumented, then instrumented, then full control
     *   When activity execution is approved
     *   Then no ACTIVITY_TASK_EXECUTION_REQUESTED event is emitted for the full-control task
     */
    @Test
    public void whenApprovedThenLaterFullControlTaskIsNotRequested() {
        ActivityRes created = executeTasks(
                "dpv-approve-later-fc",
                List.of(cliTask("deploy"), cliTask("publish"), starterTask("finish"))
        );

        postActivityApproved(created.getUuid());

        String finishUuid = taskByName(created, "finish").getUuid();
        verify(notificationClient, never()).notifyEvent(argThat(event ->
                event instanceof EmittedEventActivityTaskExecutionRequestedRes requested
                        && finishUuid.equals(requested.getEventContent().getTask().getUuid())
        ));
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Request an instrumented task
     *
     * Scenario: Requesting an instrumented task stores the provider run id and approval sets it RUNNING
     *   Given the activity is RUNNING and its instrumented task "deploy" is PENDING
     *   When the CLI requests "deploy" with provider run id "run-1"
     *   Then the response is 200
     *   And "deploy" is PENDING and its providerRunId is "run-1"
     *   And one ACTIVITY_TASK_EXECUTION_REQUESTED event is emitted
     *   When that task execution is approved
     *   Then "deploy" is RUNNING and its providerRunId is still "run-1"
     *   And the executor client is not opened for "cli"
     */
    @Test
    public void whenRequestTaskExecutionThenApprovalSetsRunningWithoutExecutor() {
        ActivityRes created = runningActivity("dpv-request", "deploy");
        clearInvocations(notificationClient);

        ResponseEntity<ActivityTaskCommandResultRes> response = requestExecution(created, "deploy", "run-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TaskRes requested = taskByName(response.getBody().getActivity(), "deploy");
        assertThat(requested.getStatus()).isEqualTo(ExecutionStatus.PENDING);
        assertThat(requested.getProviderRunId()).isEqualTo("run-1");
        assertThat(capturedTaskRequests()).hasSize(1);

        postTaskApproved(created.getUuid(), requested.getUuid());

        TaskRes running = taskByName(getActivity(created.getUuid()), "deploy");
        assertThat(running.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(running.getProviderRunId()).isEqualTo("run-1");
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Request an instrumented task
     *
     * Scenario: A task request before the activity is RUNNING is refused
     *   Given the activity and its instrumented task are PENDING
     *   When the CLI requests that task
     *   Then the response is 400
     *   And the message is "Activity <uuid> is not RUNNING"
     */
    @Test
    public void whenRequestBeforeActivityRunningThenBadRequest() {
        ActivityRes created = executeNamed("dpv-request-early", "deploy");

        ResponseEntity<ErrorRes> response = requestExecutionError(created, "deploy", "run-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Activity " + created.getUuid() + NOT_RUNNING);
        assertThat(taskByName(getActivity(created.getUuid()), "deploy").getProviderRunId()).isNull();
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Request an instrumented task
     *
     * Scenario: A request for a full-control task is refused
     *   Given the activity is RUNNING and a PENDING task names "starter"
     *   When the CLI requests that task by name
     *   Then the response is 400
     *   And the message is "Task <name> is not instrumented"
     */
    @Test
    public void whenRequestFullControlTaskThenBadRequest() {
        ActivityRes created = executeTasks("dpv-request-fc", List.of(starterTask("deploy")));
        postActivityApproved(created.getUuid());

        ResponseEntity<ErrorRes> response = requestExecutionError(created, "deploy", "run-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Task deploy is not instrumented");
        assertThat(taskByName(getActivity(created.getUuid()), "deploy").getStatus()).isEqualTo(ExecutionStatus.PENDING);
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Request an instrumented task
     *
     * Scenario: A task name that matches no task is refused
     *   Given the activity is RUNNING
     *   When the CLI requests the task name "missing"
     *   Then the response is 400
     *   And the message is "Task missing not found in activity <uuid>"
     */
    @Test
    public void whenTaskNameMissingThenBadRequest() {
        ActivityRes created = runningActivity("dpv-missing", "deploy");

        ResponseEntity<ErrorRes> response = requestExecutionError(created, "missing", "run-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage())
                .isEqualTo("Task missing not found in activity " + created.getUuid());
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Request an instrumented task
     *
     * Scenario: A task name that matches two tasks is refused
     *   Given the activity is RUNNING and two tasks are named "deploy"
     *   When the CLI requests "deploy"
     *   Then the response is 400
     *   And the message is "Task deploy matches more than one task in activity <uuid>"
     */
    @Test
    public void whenTaskNameDuplicatedThenBadRequest() {
        ActivityRes created = executeTasks("dpv-duplicate", List.of(cliTask("deploy"), cliTask("deploy")));
        postActivityApproved(created.getUuid());

        ResponseEntity<ErrorRes> response = requestExecutionError(created, "deploy", "run-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage())
                .isEqualTo("Task deploy matches more than one task in activity " + created.getUuid());
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Request an instrumented task
     *
     * Scenario: A second request while the task is PENDING replaces the provider run id
     *   Given an instrumented task is PENDING with providerRunId "run-1"
     *   When the CLI requests it again with provider run id "run-2"
     *   Then the response is 200
     *   And the providerRunId is "run-2"
     *   And a second ACTIVITY_TASK_EXECUTION_REQUESTED event is emitted
     *   When the first approval is delivered after the task is already RUNNING
     *   Then the providerRunId stays "run-2" and the status stays RUNNING
     */
    @Test
    public void whenRequestRepeatedWhilePendingThenProviderRunIdReplaced() {
        ActivityRes created = runningActivity("dpv-repeat-request", "deploy");
        clearInvocations(notificationClient);
        requestExecution(created, "deploy", "run-1");

        ResponseEntity<ActivityTaskCommandResultRes> response = requestExecution(created, "deploy", "run-2");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TaskRes requested = taskByName(response.getBody().getActivity(), "deploy");
        assertThat(requested.getProviderRunId()).isEqualTo("run-2");
        assertThat(requested.getStatus()).isEqualTo(ExecutionStatus.PENDING);
        assertThat(capturedTaskRequests()).hasSize(2);
        String taskUuid = requested.getUuid();

        postTaskApproved(created.getUuid(), taskUuid);
        postTaskApproved(created.getUuid(), taskUuid);

        TaskRes running = taskByName(getActivity(created.getUuid()), "deploy");
        assertThat(running.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(running.getProviderRunId()).isEqualTo("run-2");
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Request an instrumented task
     *
     * Scenario: Requesting a later pending task while an earlier one is still PENDING is refused
     *   Given the activity is RUNNING
     *   And an earlier instrumented task is PENDING
     *   And a later instrumented task is PENDING
     *   When the CLI requests the later task
     *   Then the response is 400
     *   And the message is "Task <later> is not the next task. Task <earlier> is still PENDING"
     *   And the later task has no providerRunId
     *   And no ACTIVITY_TASK_EXECUTION_REQUESTED event is emitted
     */
    @Test
    public void whenLaterPendingTaskRequestedThenNextTaskRefused() {
        ActivityRes created = executeTasks("dpv-later-pending", List.of(cliTask("deploy"), cliTask("publish")));
        postActivityApproved(created.getUuid());
        clearInvocations(notificationClient);

        ResponseEntity<ErrorRes> response = requestExecutionError(created, "publish", "run-2");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage())
                .isEqualTo("Task publish is not the next task. Task deploy is still PENDING");
        assertThat(taskByName(getActivity(created.getUuid()), "publish").getProviderRunId()).isNull();
        assertNoTaskExecutionRequested();
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Request an instrumented task
     *
     * Scenario: Requesting the next task while another task is RUNNING is refused
     *   Given an instrumented task is RUNNING
     *   And the next task is PENDING
     *   When the CLI requests the next task
     *   Then the response is 400
     *   And the message is "Task <next> cannot be requested while task <running> is RUNNING"
     *   And the next task has no providerRunId
     *   And no ACTIVITY_TASK_EXECUTION_REQUESTED event is emitted
     */
    @Test
    public void whenNextTaskRequestedWhileAnotherIsRunningThenRefused() {
        ActivityRes created = executeTasks("dpv-while-running", List.of(cliTask("deploy"), cliTask("publish")));
        postActivityApproved(created.getUuid());
        requestExecution(created, "deploy", "run-1");
        postTaskApproved(created.getUuid(), taskByName(created, "deploy").getUuid());
        clearInvocations(notificationClient);

        ResponseEntity<ErrorRes> response = requestExecutionError(created, "publish", "run-2");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage())
                .isEqualTo("Task publish cannot be requested while task deploy is RUNNING");
        assertThat(taskByName(getActivity(created.getUuid()), "publish").getProviderRunId()).isNull();
        assertNoTaskExecutionRequested();
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Report an instrumented task
     *
     * Scenario: Logs and results are stored while the task is RUNNING and the terminal status closes the activity
     *   Given the instrumented task "deploy" is RUNNING
     *   When the CLI appends a log and a result
     *   Then the response of each call is 200
     *   And "deploy" is still RUNNING and holds that log and that result
     *   When the CLI records SUCCEEDED
     *   Then the response is 200
     *   And "deploy" and the activity are SUCCEEDED
     *   And an activity succeeded event is emitted
     */
    @Test
    public void whenReportsThenTerminalStatusClosesTheActivity() {
        ActivityRes created = runningTask("dpv-report", "deploy", "run-1");
        clearInvocations(notificationClient);

        ResponseEntity<ActivityTaskCommandResultRes> logs = postLogs(created, "deploy", "build log");
        ResponseEntity<ActivityTaskCommandResultRes> results = postResults(created, "deploy", "saved-result");

        assertThat(logs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(results.getStatusCode()).isEqualTo(HttpStatus.OK);
        TaskRes afterResults = taskByName(results.getBody().getActivity(), "deploy");
        assertThat(afterResults.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(afterResults.getLogs()).extracting(TaskLogRes::getContent).contains("build log");
        assertThat(afterResults.getResults()).extracting(TaskResultRes::getContent).contains("saved-result");

        ResponseEntity<ActivityTaskCommandResultRes> status = postStatus(created, "deploy", ExecutionStatus.SUCCEEDED);

        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        ActivityRes closed = status.getBody().getActivity();
        assertThat(closed.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(taskByName(closed, "deploy").getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(notificationClient).notifyEvent(events.capture());
        assertThat(events.getAllValues()).anyMatch(EmittedEventActivitySucceededRes.class::isInstance);
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Report an instrumented task
     *
     * Scenario: Logs, results, and status are refused when the task is not RUNNING
     *   Given the instrumented task is PENDING
     *   When the CLI sends a log, a result, and status SUCCEEDED
     *   Then each response is 400
     *   And each message is "Task <name> is not RUNNING"
     */
    @Test
    public void whenReportWhileNotRunningThenBadRequest() {
        ActivityRes created = runningActivity("dpv-report-early", "deploy");
        String message = "Task deploy" + NOT_RUNNING;

        ResponseEntity<ErrorRes> logs = postLogsError(created, "deploy", "build log");
        ResponseEntity<ErrorRes> results = postResultsError(created, "deploy", "saved-result");
        ResponseEntity<ErrorRes> status = postStatusError(created, "deploy", ExecutionStatus.SUCCEEDED);

        assertThat(logs.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(results.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(logs.getBody().getMessage()).isEqualTo(message);
        assertThat(results.getBody().getMessage()).isEqualTo(message);
        assertThat(status.getBody().getMessage()).isEqualTo(message);
        assertThat(taskByName(getActivity(created.getUuid()), "deploy").getStatus()).isEqualTo(ExecutionStatus.PENDING);
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Report an instrumented task
     *
     * Scenario: A second terminal status is refused
     *   Given the instrumented task is already SUCCEEDED
     *   When the CLI records SUCCEEDED again
     *   Then the response is 400
     *   And the message is "Task <name> is not RUNNING"
     */
    @Test
    public void whenSecondTerminalStatusThenBadRequest() {
        ActivityRes created = executeTasks("dpv-second-status", List.of(cliTask("deploy"), cliTask("publish")));
        postActivityApproved(created.getUuid());
        requestExecution(created, "deploy", "run-1");
        postTaskApproved(created.getUuid(), taskByName(created, "deploy").getUuid());
        postStatus(created, "deploy", ExecutionStatus.SUCCEEDED);

        ResponseEntity<ErrorRes> response = postStatusError(created, "deploy", ExecutionStatus.SUCCEEDED);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Task deploy" + NOT_RUNNING);
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Report an instrumented task
     *
     * Scenario: A repeated log while the task is RUNNING stores another row
     *   Given the instrumented task is RUNNING and already has one log
     *   When the CLI appends a log with the same content
     *   Then the response is 200
     *   And the task has two logs
     */
    @Test
    public void whenLogRepeatedWhileRunningThenAnotherRowStored() {
        ActivityRes created = runningTask("dpv-repeat-log", "deploy", "run-1");
        postLogs(created, "deploy", "build log");

        ResponseEntity<ActivityTaskCommandResultRes> response = postLogs(created, "deploy", "build log");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(taskByName(response.getBody().getActivity(), "deploy").getLogs())
                .extracting(TaskLogRes::getContent)
                .containsExactly("build log", "build log");
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Report an instrumented task
     *
     * Scenario: A FAILED status on an instrumented task fails the activity and cancels tasks still PENDING
     *   Given the first task is an instrumented task RUNNING and a later task is PENDING
     *   When the CLI records FAILED for the running task
     *   Then that task is FAILED
     *   And the later task is CANCELED
     *   And the activity is FAILED
     */
    @Test
    public void whenInstrumentedTaskFailsThenLaterPendingTasksCanceled() {
        ActivityRes created = executeTasks("dpv-fail-cli", List.of(cliTask("deploy"), cliTask("publish")));
        postActivityApproved(created.getUuid());
        requestExecution(created, "deploy", "run-1");
        postTaskApproved(created.getUuid(), taskByName(created, "deploy").getUuid());

        ResponseEntity<ActivityTaskCommandResultRes> response = postStatus(created, "deploy", ExecutionStatus.FAILED);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ActivityRes failed = response.getBody().getActivity();
        assertThat(failed.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(taskByName(failed, "deploy").getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(taskByName(failed, "publish").getStatus()).isEqualTo(ExecutionStatus.CANCELED);
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Report an instrumented task
     *
     * Scenario: A CANCELED status on an instrumented task cancels the activity
     *   Given the instrumented task is RUNNING
     *   When the CLI records CANCELED
     *   Then the task is CANCELED
     *   And the activity is CANCELED
     */
    @Test
    public void whenInstrumentedTaskCanceledThenActivityCanceled() {
        ActivityRes created = runningTask("dpv-cancel-status", "deploy", "run-1");

        ResponseEntity<ActivityTaskCommandResultRes> response = postStatus(created, "deploy", ExecutionStatus.CANCELED);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getActivity().getStatus()).isEqualTo(ExecutionStatus.CANCELED);
        assertThat(taskByName(response.getBody().getActivity(), "deploy").getStatus()).isEqualTo(ExecutionStatus.CANCELED);
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Hand off between modes
     *
     * Scenario: After an instrumented task succeeds, the following full-control task is requested
     *   Given the activity has an instrumented task and then a full-control task
     *   And the instrumented task is RUNNING
     *   When the CLI records SUCCEEDED
     *   Then an ACTIVITY_TASK_EXECUTION_REQUESTED event is emitted for the full-control task
     *   And the full-control task is still PENDING on that response
     */
    @Test
    public void whenInstrumentedThenFullControlThenSecondTaskRequested() {
        ActivityRes created = executeTasks("dpv-cli-then-fc", List.of(cliTask("deploy"), starterTask("publish")));
        postActivityApproved(created.getUuid());
        requestExecution(created, "deploy", "run-1");
        postTaskApproved(created.getUuid(), taskByName(created, "deploy").getUuid());
        clearInvocations(notificationClient);

        ResponseEntity<ActivityTaskCommandResultRes> response = postStatus(created, "deploy", ExecutionStatus.SUCCEEDED);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(taskByName(response.getBody().getActivity(), "publish").getStatus()).isEqualTo(ExecutionStatus.PENDING);
        assertThat(capturedTaskRequests()).singleElement().satisfies(event ->
                assertThat(event.getEventContent().getTask().getUuid()).isEqualTo(taskByName(created, "publish").getUuid())
        );
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Hand off between modes
     *
     * Scenario: After a full-control task ends, the following instrumented task stays PENDING
     *   Given the activity has a full-control task and then an instrumented task
     *   When the full-control task reaches SUCCEEDED
     *   Then the instrumented task is PENDING
     *   And Advance Activity does not emit ACTIVITY_TASK_EXECUTION_REQUESTED for it
     */
    @Test
    public void whenFullControlThenInstrumentedThenSecondTaskStaysPending() {
        stubSucceededExecutor();
        ActivityRes created = executeTasks("dpv-fc-then-cli", List.of(starterTask("deploy"), cliTask("report")));
        postActivityApproved(created.getUuid());
        clearInvocations(notificationClient);

        postTaskApproved(created.getUuid(), taskByName(created, "deploy").getUuid());

        ActivityRes stored = getActivity(created.getUuid());
        assertThat(taskByName(stored, "deploy").getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(taskByName(stored, "report").getStatus()).isEqualTo(ExecutionStatus.PENDING);
        assertNoTaskExecutionRequested();
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Results on a full-control task
     *
     * Scenario: Results uploaded while a full-control task is RUNNING are still there after it succeeds
     *   Given a full-control task is RUNNING and its status read is held
     *   When the caller appends a result for that task
     *   And the executor then reports SUCCEEDED
     *   Then the task is SUCCEEDED
     *   And the result is still stored
     */
    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    public void whenFullControlTaskRunningThenResultsRemainAfterSuccess() throws Exception {
        CountDownLatch statusEntered = new CountDownLatch(1);
        CountDownLatch releaseStatus = new CountDownLatch(1);
        stubHeldStatus(statusEntered, releaseStatus);
        ActivityRes created = executeTasks("dpv-fc-results", List.of(starterTask("deploy")));
        postActivityApproved(created.getUuid());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread approval = new Thread(() -> {
            try {
                postTaskApproved(created.getUuid(), taskByName(created, "deploy").getUuid());
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        }, "full-control-approval");
        approval.setDaemon(true);
        approval.start();
        try {
            assertThat(statusEntered.await(20, TimeUnit.SECONDS)).isTrue();
            assertThat(taskByName(getActivity(created.getUuid()), "deploy").getStatus()).isEqualTo(ExecutionStatus.RUNNING);
            ResponseEntity<ActivityTaskCommandResultRes> recorded = postResultsByUuid(created.getUuid(), "deploy", "saved-result");
            assertThat(recorded.getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            releaseStatus.countDown();
            approval.join(20_000);
        }

        assertThat(failure.get()).isNull();
        assertThat(approval.isAlive()).isFalse();
        TaskRes finished = taskByName(getActivity(created.getUuid()), "deploy");
        assertThat(finished.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(finished.getResults()).extracting(TaskResultRes::getContent).contains("saved-result");
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Cancel an activity that has instrumented tasks
     *
     * Scenario: Cancel sets every PENDING task to CANCELED
     *   Given an activity is RUNNING and its instrumented tasks are PENDING
     *   When the user cancels the activity
     *   Then the response is 200
     *   And the activity and every task are CANCELED
     *   And the executor client is not opened
     */
    @Test
    public void whenCancelPendingInstrumentedThenActivityCanceled() {
        ActivityRes created = executeTasks("dpv-cancel-pending-cli", List.of(cliTask("deploy"), cliTask("publish")));
        postActivityApproved(created.getUuid());

        ResponseEntity<ActivityCancelResultRes> response = postCancel(created.getUuid());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ActivityRes canceled = response.getBody().getActivity();
        assertThat(canceled.getStatus()).isEqualTo(ExecutionStatus.CANCELED);
        assertThat(canceled.getTasks()).allSatisfy(task -> assertThat(task.getStatus()).isEqualTo(ExecutionStatus.CANCELED));
        verify(executorClientFactory, never()).getExecutorClient(anyString(), anyString());
    }

    /**
     * Feature: Cancel an activity that has instrumented tasks
     *
     * Scenario: Cancel waits for the CLI to close a RUNNING instrumented task and does not call its executor
     *   Given an instrumented task is RUNNING and another task is PENDING
     *   When the user cancels the activity
     *   Then the PENDING task becomes CANCELED and the RUNNING task stays RUNNING
     *   When the CLI records CANCELED for the running task while the cancel call is waiting
     *   Then the cancel response is 200
     *   And the activity is CANCELED
     *   And the executor client is not opened for "cli"
     */
    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    public void whenCancelRunningInstrumentedThenWaitsForCliStatus() throws Exception {
        ActivityRes created = executeTasks("dpv-cancel-running-cli", List.of(cliTask("deploy"), cliTask("publish")));
        postActivityApproved(created.getUuid());
        requestExecution(created, "deploy", "run-1");
        postTaskApproved(created.getUuid(), taskByName(created, "deploy").getUuid());
        AtomicReference<ResponseEntity<ActivityCancelResultRes>> response = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread cancelThread = new Thread(() -> {
            try {
                response.set(postCancel(created.getUuid()));
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        }, "cancel-activity");
        cancelThread.setDaemon(true);
        cancelThread.start();
        try {
            awaitActivity(created.getUuid(), activity ->
                    taskByName(activity, "publish").getStatus() == ExecutionStatus.CANCELED
                            && taskByName(activity, "deploy").getStatus() == ExecutionStatus.RUNNING
            );
            ResponseEntity<ActivityTaskCommandResultRes> status = postStatus(created, "deploy", ExecutionStatus.CANCELED);
            assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
            cancelThread.join(20_000);
        } finally {
            if (cancelThread.isAlive()) {
                postStatus(created, "deploy", ExecutionStatus.CANCELED);
                cancelThread.join(10_000);
            }
        }

        assertThat(failure.get()).isNull();
        assertThat(cancelThread.isAlive()).isFalse();
        assertThat(response.get().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.get().getBody().getActivity().getStatus()).isEqualTo(ExecutionStatus.CANCELED);
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Several instrumented tasks
     *
     * Scenario: After the earlier instrumented task has a terminal status, the next instrumented task can be requested
     *   Given two instrumented tasks and the earlier one has a terminal status
     *   And the next task is PENDING
     *   And no other task is RUNNING
     *   When the CLI requests the next task with a provider run id
     *   Then the response is 200
     *   And that task is PENDING and stores the provider run id
     *   And one ACTIVITY_TASK_EXECUTION_REQUESTED event is emitted
     */
    @Test
    public void whenEarlierInstrumentedTaskTerminalThenNextTaskCanBeRequested() {
        ActivityRes created = executeTasks("dpv-next-after-terminal", List.of(cliTask("deploy"), cliTask("publish")));
        postActivityApproved(created.getUuid());
        requestExecution(created, "deploy", "run-1");
        postTaskApproved(created.getUuid(), taskByName(created, "deploy").getUuid());
        postStatus(created, "deploy", ExecutionStatus.SUCCEEDED);
        clearInvocations(notificationClient);

        ResponseEntity<ActivityTaskCommandResultRes> response = requestExecution(created, "publish", "run-2");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TaskRes next = taskByName(response.getBody().getActivity(), "publish");
        assertThat(next.getStatus()).isEqualTo(ExecutionStatus.PENDING);
        assertThat(next.getProviderRunId()).isEqualTo("run-2");
        assertThat(capturedTaskRequests()).hasSize(1);
        assertCliExecutorNotOpened();
    }

    /**
     * Feature: Reject an instrumented task
     *
     * Scenario: A rejected instrumented task fails the activity
     *   Given the CLI has requested an instrumented task and it is still PENDING
     *   When task execution is rejected
     *   Then that task is FAILED
     *   And the activity is FAILED
     */
    @Test
    public void whenTaskExecutionRejectedThenActivityFailed() {
        ActivityRes created = runningActivity("dpv-reject-cli", "deploy");
        requestExecution(created, "deploy", "run-1");
        String taskUuid = taskByName(getActivity(created.getUuid()), "deploy").getUuid();

        postTaskRejected(created.getUuid(), taskUuid);

        ActivityRes stored = getActivity(created.getUuid());
        assertThat(taskByName(stored, "deploy").getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(stored.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertCliExecutorNotOpened();
    }

    private ActivityRes runningTask(String dataProductVersionUuid, String taskName, String providerRunId) {
        ActivityRes created = runningActivity(dataProductVersionUuid, taskName);
        requestExecution(created, taskName, providerRunId);
        postTaskApproved(created.getUuid(), taskByName(created, taskName).getUuid());
        ActivityRes running = getActivity(created.getUuid());
        assertThat(taskByName(running, taskName).getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        return running;
    }

    private ActivityRes runningActivity(String dataProductVersionUuid, String taskName) {
        ActivityRes created = executeNamed(dataProductVersionUuid, taskName);
        postActivityApproved(created.getUuid());
        ActivityRes running = getActivity(created.getUuid());
        assertThat(running.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(taskByName(running, taskName).getStatus()).isEqualTo(ExecutionStatus.PENDING);
        return running;
    }

    private ActivityRes executeNamed(String dataProductVersionUuid, String taskName) {
        return executeTasks(dataProductVersionUuid, List.of(cliTask(taskName)));
    }

    private ActivityRes executeTasks(String dataProductVersionUuid, List<TaskRes> tasks) {
        ActivityRes activity = new ActivityRes();
        activity.setDataProductVersionUuid(dataProductVersionUuid);
        activity.setDataProductFqn(dataProductVersionUuid);
        activity.setDataProductVersionTag("1.0.0");
        activity.setName("prod");
        activity.setSortOrder(1);
        activity.setTasks(tasks);
        ActivityExecuteCommandRes command = new ActivityExecuteCommandRes();
        command.setActivity(activity);
        ResponseEntity<ActivityExecuteResultRes> response = rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES_EXECUTE),
                new HttpEntity<>(command, jsonHeaders()),
                ActivityExecuteResultRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().getActivity();
    }

    private ResponseEntity<ActivityTaskCommandResultRes> requestExecution(ActivityRes activity, String taskName, String providerRunId) {
        return rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES_TASKS_REQUEST_EXECUTION),
                new HttpEntity<>(requestCommand(activity, taskName, providerRunId), jsonHeaders()),
                ActivityTaskCommandResultRes.class
        );
    }

    private ResponseEntity<ErrorRes> requestExecutionError(ActivityRes activity, String taskName, String providerRunId) {
        return rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES_TASKS_REQUEST_EXECUTION),
                new HttpEntity<>(requestCommand(activity, taskName, providerRunId), jsonHeaders()),
                ErrorRes.class
        );
    }

    private ResponseEntity<ActivityTaskCommandResultRes> postLogs(ActivityRes activity, String taskName, String content) {
        return rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES_TASKS_LOGS),
                new HttpEntity<>(logsCommand(activity, taskName, content), jsonHeaders()),
                ActivityTaskCommandResultRes.class
        );
    }

    private ResponseEntity<ErrorRes> postLogsError(ActivityRes activity, String taskName, String content) {
        return rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES_TASKS_LOGS),
                new HttpEntity<>(logsCommand(activity, taskName, content), jsonHeaders()),
                ErrorRes.class
        );
    }

    private ResponseEntity<ActivityTaskCommandResultRes> postResults(ActivityRes activity, String taskName, String content) {
        return rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES_TASKS_RESULTS),
                new HttpEntity<>(resultsCommand(activity, taskName, content), jsonHeaders()),
                ActivityTaskCommandResultRes.class
        );
    }

    private ResponseEntity<ActivityTaskCommandResultRes> postResultsByUuid(String activityUuid, String taskName, String content) {
        ActivityTaskResultsCommandRes command = resultsCommand(null, taskName, content);
        command.setActivityUuid(activityUuid);
        return rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES_TASKS_RESULTS),
                new HttpEntity<>(command, jsonHeaders()),
                ActivityTaskCommandResultRes.class
        );
    }

    private ResponseEntity<ErrorRes> postResultsError(ActivityRes activity, String taskName, String content) {
        return rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES_TASKS_RESULTS),
                new HttpEntity<>(resultsCommand(activity, taskName, content), jsonHeaders()),
                ErrorRes.class
        );
    }

    private ResponseEntity<ActivityTaskCommandResultRes> postStatus(ActivityRes activity, String taskName, ExecutionStatus status) {
        return rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES_TASKS_STATUS),
                new HttpEntity<>(statusCommand(activity, taskName, status), jsonHeaders()),
                ActivityTaskCommandResultRes.class
        );
    }

    private ResponseEntity<ErrorRes> postStatusError(ActivityRes activity, String taskName, ExecutionStatus status) {
        return rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES_TASKS_STATUS),
                new HttpEntity<>(statusCommand(activity, taskName, status), jsonHeaders()),
                ErrorRes.class
        );
    }

    private ResponseEntity<ActivityCancelResultRes> postCancel(String activityUuid) {
        ActivityRes activity = new ActivityRes();
        activity.setUuid(activityUuid);
        ActivityCancelCommandRes command = new ActivityCancelCommandRes();
        command.setActivity(activity);
        return rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES_CANCEL),
                new HttpEntity<>(command, jsonHeaders()),
                ActivityCancelResultRes.class
        );
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

    private ActivityRes awaitActivity(String uuid, Predicate<ActivityRes> condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        ActivityRes latest = null;
        while (System.nanoTime() < deadline) {
            latest = getActivity(uuid);
            if (condition.test(latest)) {
                return latest;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for activity " + uuid);
    }

    private void stubSucceededExecutor() {
        ExecutorClient client = mock(ExecutorClient.class);
        when(executorClientFactory.getExecutorClient(eq("starter"), anyString())).thenReturn(client);
        when(client.startTask(any())).thenAnswer(invocation -> {
            ExecutorTaskStartResultRes result = new ExecutorTaskStartResultRes();
            result.setProviderRunId("run-starter");
            return result;
        });
        ExecutorTaskStatusRes status = new ExecutorTaskStatusRes();
        status.setStatus("SUCCEEDED");
        when(client.getTaskStatus(anyString())).thenReturn(status);
    }

    private void stubHeldStatus(CountDownLatch entered, CountDownLatch release) {
        ExecutorClient client = mock(ExecutorClient.class);
        when(executorClientFactory.getExecutorClient(eq("starter"), anyString())).thenReturn(client);
        when(client.startTask(any())).thenAnswer(invocation -> {
            ExecutorTaskStartResultRes result = new ExecutorTaskStartResultRes();
            result.setProviderRunId("run-held");
            return result;
        });
        when(client.getTaskStatus(anyString())).thenAnswer(invocation -> {
            entered.countDown();
            try {
                if (!release.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("status read was not released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while holding the status read", interrupted);
            }
            ExecutorTaskStatusRes status = new ExecutorTaskStatusRes();
            status.setStatus("SUCCEEDED");
            return status;
        });
    }

    private List<EmittedEventActivityTaskExecutionRequestedRes> capturedTaskRequests() {
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(notificationClient, atLeastOnce()).notifyEvent(events.capture());
        return events.getAllValues().stream()
                .filter(EmittedEventActivityTaskExecutionRequestedRes.class::isInstance)
                .map(EmittedEventActivityTaskExecutionRequestedRes.class::cast)
                .toList();
    }

    private void assertNoTaskExecutionRequested() {
        verify(notificationClient, never()).notifyEvent(argThat(
                EmittedEventActivityTaskExecutionRequestedRes.class::isInstance
        ));
    }

    private void assertCliExecutorNotOpened() {
        verify(executorClientFactory, never()).getExecutorClient(eq("cli"), anyString());
    }

    private static ActivityTaskExecutionRequestCommandRes requestCommand(ActivityRes activity, String taskName, String providerRunId) {
        ActivityTaskExecutionRequestCommandRes command = new ActivityTaskExecutionRequestCommandRes();
        command.setDataProductFqn(activity.getDataProductFqn());
        command.setDataProductVersionTag(activity.getDataProductVersionTag());
        command.setActivityName(activity.getName());
        command.setTaskName(taskName);
        command.setProviderRunId(providerRunId);
        return command;
    }

    private static ActivityTaskLogsCommandRes logsCommand(ActivityRes activity, String taskName, String content) {
        TaskLogRes log = new TaskLogRes();
        log.setContent(content);
        ActivityTaskLogsCommandRes command = new ActivityTaskLogsCommandRes();
        command.setDataProductFqn(activity.getDataProductFqn());
        command.setDataProductVersionTag(activity.getDataProductVersionTag());
        command.setActivityName(activity.getName());
        command.setTaskName(taskName);
        command.setLogs(List.of(log));
        return command;
    }

    private static ActivityTaskResultsCommandRes resultsCommand(ActivityRes activity, String taskName, String content) {
        TaskResultRes result = new TaskResultRes();
        result.setContent(content);
        ActivityTaskResultsCommandRes command = new ActivityTaskResultsCommandRes();
        if (activity != null) {
            command.setDataProductFqn(activity.getDataProductFqn());
            command.setDataProductVersionTag(activity.getDataProductVersionTag());
            command.setActivityName(activity.getName());
        }
        command.setTaskName(taskName);
        command.setResults(List.of(result));
        return command;
    }

    private static ActivityTaskStatusCommandRes statusCommand(ActivityRes activity, String taskName, ExecutionStatus status) {
        ActivityTaskStatusCommandRes command = new ActivityTaskStatusCommandRes();
        command.setDataProductFqn(activity.getDataProductFqn());
        command.setDataProductVersionTag(activity.getDataProductVersionTag());
        command.setActivityName(activity.getName());
        command.setTaskName(taskName);
        command.setStatus(status);
        return command;
    }

    private static TaskRes taskByName(ActivityRes activity, String name) {
        return activity.getTasks().stream()
                .filter(task -> name.equals(task.getName()))
                .findFirst()
                .orElseThrow();
    }

    private static TaskRes cliTask(String name) {
        TaskRes task = new TaskRes();
        task.setName(name);
        task.setExecutorName("cli");
        return task;
    }

    private static TaskRes starterTask(String name) {
        TaskRes task = new TaskRes();
        task.setName(name);
        task.setExecutorName("starter");
        task.setExecutorParameters(executorParameters());
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

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
