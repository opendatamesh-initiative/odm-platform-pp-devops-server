package org.opendatamesh.platform.pp.devops.rest.v2.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opendatamesh.platform.pp.devops.activity.entities.GitRefType;
import org.opendatamesh.platform.pp.devops.activity.repositories.ActivitiesRepository;
import org.opendatamesh.platform.pp.devops.client.notification.NotificationClient;
import org.opendatamesh.platform.pp.devops.executor.ExecutorSecretsStore;
import org.opendatamesh.platform.pp.devops.rest.v2.DevOpsApplicationIT;
import org.opendatamesh.platform.pp.devops.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.ErrorRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ExecutorParametersRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.GitRefRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.DataProductRepoRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskLogRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskResultRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.events.emitted.EmittedEventActivityExecutionRequestedRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.execute.ActivityExecuteCommandRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.usecases.execute.ActivityExecuteResultRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.event.EventTypeRes;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Execute an activity and keep executor secrets in memory.
 * Scenarios trace to {@code spdd/prompt/BDMD-5437-202609290955-[Feat]-service-full-control-happy-path.md}.
 */
public class ActivityUseCaseControllerIT extends DevOpsApplicationIT {

    private static final String SECRET = "stored-token";
    private static final String OTHER_SECRET = "other-token";

    @Autowired
    private ActivitiesRepository activitiesRepository;

    @Autowired
    private NotificationClient notificationClient;

    @Autowired
    private ExecutorSecretsStore executorSecretsStore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void resetState() {
        activitiesRepository.deleteAll();
        reset(notificationClient);
    }

    /**
     * Feature: Execute an activity
     *
     * Scenario: An executable activity is created PENDING and its execution is requested
     *   Given the executor "starter" is declared in full-control mode
     *   And no activity "prod" is open for the data product version
     *   When the UI executes activity "prod" with sortOrder 2 and two tasks on "starter", one with a pipeline parameter containing a placeholder
     *   Then the response is 201
     *   And the activity and both tasks are PENDING
     *   And the tasks have sortOrder 0 and 1 and the activity keeps sortOrder 2
     *   And executor parameters and pipeline parameters are stored as sent, placeholder included
     *   And one ACTIVITY_EXECUTION_REQUESTED event is emitted with the activity, its sortOrder, and its tasks without logs or results
     */
    @Test
    public void whenExecuteActivityThenPendingAndExecutionRequested() throws Exception {
        ActivityRes activity = executableActivity("dpv-execute", "prod", 2);
        activity.getTasks().get(0).setPipelineParameters(Map.of(
                "infraEndpoint", "${dev.results.deploy-infrastructure.endpoint}"
        ));

        ResponseEntity<ActivityExecuteResultRes> response = execute(activity, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ActivityRes created = response.getBody().getActivity();
        assertThat(created.getStatus()).isEqualTo(ExecutionStatus.PENDING);
        assertThat(created.getSortOrder()).isEqualTo(2);
        assertThat(created.getTasks()).hasSize(2);
        assertThat(created.getTasks()).allSatisfy(task -> assertThat(task.getStatus()).isEqualTo(ExecutionStatus.PENDING));
        assertThat(created.getTasks()).extracting(TaskRes::getSortOrder).containsExactlyInAnyOrder(0, 1);
        assertThat(created.getTasks()).anySatisfy(task ->
                assertThat(task.getPipelineParameters()).containsEntry(
                        "infraEndpoint", "${dev.results.deploy-infrastructure.endpoint}"
                )
        );
        assertThat(created.getTasks()).allSatisfy(task -> {
            assertThat(task.getExecutorName()).isEqualTo("starter");
            assertThat(task.getExecutorParameters().getDataProductRepo().getProviderType()).isEqualTo("GITHUB");
            assertThat(task.getExecutorParameters().getRef().getName()).isEqualTo("v1.2.0");
            assertThat(task.getExecutorParameters().getRef().getType()).isEqualTo(GitRefType.TAG);
        });

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(notificationClient, times(1)).notifyEvent(events.capture());
        assertThat(events.getValue()).isInstanceOf(EmittedEventActivityExecutionRequestedRes.class);
        EmittedEventActivityExecutionRequestedRes event = (EmittedEventActivityExecutionRequestedRes) events.getValue();
        assertThat(event.getType()).isEqualTo(EventTypeRes.ACTIVITY_EXECUTION_REQUESTED);
        assertThat(event.getEventContent().getActivity().getSortOrder()).isEqualTo(2);
        assertThat(event.getEventContent().getActivity().getTasks()).hasSize(2);
        assertThat(event.getEventContent().getActivity().getTasks()).allSatisfy(task -> {
            assertThat(task.getLogs()).isNull();
            assertThat(task.getResults()).isNull();
        });
    }

    /**
     * Feature: Execute an activity
     *
     * Scenario: Fields reserved to DevOps are ignored
     *   Given an execute request whose activity and tasks carry a status, a providerRunId, timestamps, logs, and results
     *   When the UI executes the activity
     *   Then the response is 201
     *   And the activity and tasks are PENDING with no providerRunId, no timestamps, no logs, and no results
     */
    @Test
    public void whenExecuteWithReservedFieldsThenReset() {
        ActivityRes activity = executableActivity("dpv-reserved", "prod", 1);
        Date stamp = new Date(1_700_000_000_000L);
        activity.setStatus(ExecutionStatus.RUNNING);
        activity.setStartedAt(stamp);
        activity.setFinishedAt(stamp);
        for (TaskRes task : activity.getTasks()) {
            task.setStatus(ExecutionStatus.SUCCEEDED);
            task.setProviderRunId("external-run");
            task.setStartedAt(stamp);
            task.setFinishedAt(stamp);
            TaskLogRes log = new TaskLogRes();
            log.setContent("ignored-log");
            task.setLogs(List.of(log));
            TaskResultRes result = new TaskResultRes();
            result.setContent("{\"v\":\"ignored\"}");
            task.setResults(List.of(result));
        }

        ResponseEntity<ActivityExecuteResultRes> response = execute(activity, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ActivityRes created = response.getBody().getActivity();
        assertThat(created.getStatus()).isEqualTo(ExecutionStatus.PENDING);
        assertThat(created.getStartedAt()).isNull();
        assertThat(created.getFinishedAt()).isNull();
        assertThat(created.getTasks()).allSatisfy(task -> {
            assertThat(task.getStatus()).isEqualTo(ExecutionStatus.PENDING);
            assertThat(task.getProviderRunId()).isNull();
            assertThat(task.getStartedAt()).isNull();
            assertThat(task.getFinishedAt()).isNull();
            assertThat(task.getLogs()).isNullOrEmpty();
            assertThat(task.getResults()).isNullOrEmpty();
        });
    }

    /**
     * Feature: Execute an activity
     *
     * Scenario: A request that cannot run is refused
     *   Given an execute request with no tasks, or without the activity sortOrder, or with a task without executor name or executor parameters
     *   When the UI executes the activity
     *   Then the response is 400
     *   And no activity is stored and no event is emitted
     */
    @Test
    public void whenExecuteNotExecutableThenBadRequest() {
        ActivityRes noTasks = namedActivity("dpv-refuse", "prod", 1);
        noTasks.setTasks(List.of());
        assertBadRequest(noTasks, "Activity must have at least one task");

        ActivityRes noSortOrder = executableActivity("dpv-refuse", "prod", 1);
        noSortOrder.setSortOrder(null);
        assertBadRequest(noSortOrder, "Activity sort order is required");

        ActivityRes noExecutor = executableActivity("dpv-refuse", "prod", 1);
        noExecutor.getTasks().get(0).setExecutorName(" ");
        assertBadRequest(noExecutor, "Task deploy: executor name is required");

        ActivityRes noParameters = executableActivity("dpv-refuse", "prod", 1);
        noParameters.getTasks().get(0).setExecutorParameters(null);
        assertBadRequest(noParameters, "Task deploy: executor parameters are required");

        assertThat(activitiesRepository.count()).isZero();
        verify(notificationClient, never()).notifyEvent(any());
    }

    /**
     * Feature: Execute an activity
     *
     * Scenario: Executor parameters without the provider-neutral required fields are refused
     *   Given an execute request whose task executor parameters lack repository.providerType, ref.name, or ref.type
     *   When the UI executes the activity
     *   Then the response is 400
     */
    @Test
    public void whenExecutorParametersIncompleteThenBadRequest() {
        ActivityRes noProviderType = executableActivity("dpv-params", "prod", 1);
        noProviderType.getTasks().get(0).getExecutorParameters().getDataProductRepo().setProviderType(" ");
        assertBadRequest(noProviderType, "Task deploy: executor parameters repository provider type is required");

        ActivityRes noRefName = executableActivity("dpv-params", "prod", 1);
        noRefName.getTasks().get(0).getExecutorParameters().getRef().setName(" ");
        assertBadRequest(noRefName, "Task deploy: executor parameters ref name is required");

        ActivityRes noRefType = executableActivity("dpv-params", "prod", 1);
        noRefType.getTasks().get(0).getExecutorParameters().getRef().setType(null);
        assertBadRequest(noRefType, "Task deploy: executor parameters ref type is required");
    }

    /**
     * Feature: Execute an activity
     *
     * Scenario: Provider-specific fields are not checked by DevOps
     *   Given an execute request on a GitHub repository with no pipelineIdentifier, no ownerId, and no ownerType
     *   When the UI executes the activity
     *   Then the response is 201
     */
    @Test
    public void whenProviderSpecificFieldsMissingThenCreated() {
        ActivityRes activity = executableActivity("dpv-github", "prod", 1);
        ExecutorParametersRes parameters = activity.getTasks().get(0).getExecutorParameters();
        parameters.setPipelineIdentifier(null);
        parameters.getDataProductRepo().setProviderType("GITHUB");
        parameters.getDataProductRepo().setOwnerId(null);
        parameters.getDataProductRepo().setOwnerType(null);

        ResponseEntity<ActivityExecuteResultRes> response = execute(activity, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /**
     * Feature: Execute an activity
     *
     * Scenario: A pipeline parameter with a blank key or a null value is refused
     *   Given an execute request with a pipeline parameter whose key is blank or whose value is null
     *   When the UI executes the activity
     *   Then the response is 400
     */
    @Test
    public void whenPipelineParameterInvalidThenBadRequest() {
        ActivityRes blankKey = executableActivity("dpv-pipeline", "prod", 1);
        blankKey.getTasks().get(0).setPipelineParameters(new LinkedHashMap<>(Map.of(" ", "value")));
        assertBadRequest(blankKey, "Task deploy: pipeline parameter keys cannot be blank");

        ActivityRes nullValue = executableActivity("dpv-pipeline", "prod", 1);
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("region", null);
        nullValue.getTasks().get(0).setPipelineParameters(parameters);
        assertBadRequest(nullValue, "Task deploy: pipeline parameter region has no value");
    }

    /**
     * Feature: Execute an activity
     *
     * Scenario: An undeclared executor is refused
     *   Given an execute request whose task names the executor "unknown"
     *   When the UI executes the activity
     *   Then the response is 400
     *   And the error message is "Executor unknown is not declared"
     */
    @Test
    public void whenExecutorUndeclaredThenBadRequest() {
        ActivityRes activity = executableActivity("dpv-unknown", "prod", 1);
        activity.getTasks().get(0).setExecutorName("unknown");
        assertBadRequest(activity, "Executor unknown is not declared");
    }

    /**
     * Feature: Execute an activity
     *
     * Scenario: An instrumented executor is refused in this story
     *   Given the executor "cli" is declared in instrumented mode
     *   When the UI executes an activity with a task on "cli"
     *   Then the response is 400
     */
    @Test
    public void whenExecutorInstrumentedThenBadRequest() {
        ActivityRes activity = executableActivity("dpv-cli", "prod", 1);
        activity.getTasks().get(0).setExecutorName("cli");
        assertBadRequest(activity, "Executor cli runs in instrumented mode, which is not supported yet");
    }

    /**
     * Feature: Execute an activity
     *
     * Scenario: A second execution of the same activity is refused while one is open
     *   Given activity "prod" of the data product version is PENDING
     *   When the UI executes activity "prod" again for the same data product version
     *   Then the response is 400
     *   And executing activity "dev" for the same data product version returns 201
     */
    @Test
    public void whenSameActivityOpenThenBadRequestAndOtherNameCreated() {
        assertThat(execute(executableActivity("dpv-open", "prod", 1), null).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ErrorRes> duplicate = executeExpectingError(executableActivity("dpv-open", "prod", 1));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(duplicate.getBody().getMessage())
                .isEqualTo("Activity prod of data product version dpv-open is already PENDING or RUNNING");

        assertThat(execute(executableActivity("dpv-open", "dev", 1), null).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /**
     * Feature: Executor secrets
     *
     * Scenario: Secret headers of the task executor are kept in memory, rewritten
     *   Given an execute request with header x-odm-starter-executor-secret-token and header x-odm-other-executor-secret-token
     *   When the UI executes an activity with tasks on "starter"
     *   Then the secrets store holds x-odm-token for executor "starter" and the new activity
     *   And nothing is stored for executor "other"
     *   And neither the response nor the emitted event contains the secret value
     */
    @Test
    public void whenExecuteWithSecretHeadersThenStoredRewrittenForTaskExecutorOnly() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-odm-starter-executor-secret-token", SECRET);
        headers.add("x-odm-other-executor-secret-token", OTHER_SECRET);

        ResponseEntity<ActivityExecuteResultRes> response = execute(executableActivity("dpv-secrets", "prod", 1), headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String activityUuid = response.getBody().getActivity().getUuid();
        assertThat(executorSecretsStore.find("starter", activityUuid)).containsEntry("x-odm-token", SECRET);
        assertThat(executorSecretsStore.find("other", activityUuid)).isEmpty();
        assertThat(objectMapper.writeValueAsString(response.getBody())).doesNotContain(SECRET, OTHER_SECRET);

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(notificationClient).notifyEvent(events.capture());
        assertThat(objectMapper.writeValueAsString(events.getValue())).doesNotContain(SECRET, OTHER_SECRET);
    }

    private void assertBadRequest(ActivityRes activity, String message) {
        ResponseEntity<ErrorRes> response = executeExpectingError(activity);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo(message);
    }

    private ResponseEntity<ActivityExecuteResultRes> execute(ActivityRes activity, HttpHeaders headers) {
        return rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES_EXECUTE),
                new HttpEntity<>(command(activity), jsonHeaders(headers)),
                ActivityExecuteResultRes.class
        );
    }

    private ResponseEntity<ErrorRes> executeExpectingError(ActivityRes activity) {
        return rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES_EXECUTE),
                new HttpEntity<>(command(activity), jsonHeaders(null)),
                ErrorRes.class
        );
    }

    private static ActivityExecuteCommandRes command(ActivityRes activity) {
        ActivityExecuteCommandRes command = new ActivityExecuteCommandRes();
        command.setActivity(activity);
        return command;
    }

    private static HttpHeaders jsonHeaders(HttpHeaders extra) {
        HttpHeaders headers = new HttpHeaders();
        if (extra != null) {
            headers.addAll(extra);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static ActivityRes executableActivity(String dataProductVersionUuid, String name, int sortOrder) {
        ActivityRes activity = namedActivity(dataProductVersionUuid, name, sortOrder);
        activity.setTasks(List.of(task("deploy"), task("publish")));
        return activity;
    }

    private static ActivityRes namedActivity(String dataProductVersionUuid, String name, int sortOrder) {
        ActivityRes activity = new ActivityRes();
        activity.setDataProductVersionUuid(dataProductVersionUuid);
        activity.setName(name);
        activity.setSortOrder(sortOrder);
        return activity;
    }

    private static TaskRes task(String name) {
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
}
