package org.opendatamesh.platform.pp.devops.rest.v2.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.repositories.ActivitiesRepository;
import org.opendatamesh.platform.pp.devops.rest.v2.DevOpsApplicationIT;
import org.opendatamesh.platform.pp.devops.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.ErrorRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskLogRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskResultRes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ActivityControllerIT extends DevOpsApplicationIT {

    @Autowired
    private ActivitiesRepository activitiesRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.jpa.properties.hibernate.default_schema}")
    private String schema;

    @BeforeEach
    void cleanActivities() {
        activitiesRepository.deleteAll();
    }

    /**
     * Scenario: Omitted status defaults to PENDING and the server assigns the uuid
     *   Given an activity body with dataProductVersionUuid and name and no status and no uuid
     *   When the client POSTs /api/v2/pp/devops/activities
     *   Then the response is 201
     *   And status is PENDING
     *   And the response uuid is present
     *   And dataProductVersionUuid and name match the body
     */
    @Test
    public void whenCreateActivityWithoutStatusThenPendingAndServerUuid() {
        ActivityRes payload = newActivity("dpv-create-pending", "create-pending");

        ResponseEntity<ActivityRes> response = rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES),
                new HttpEntity<>(payload),
                ActivityRes.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUuid()).isNotBlank();
        assertThat(response.getBody().getStatus()).isEqualTo(ExecutionStatus.PENDING);
        assertThat(response.getBody().getDataProductVersionUuid()).isEqualTo(payload.getDataProductVersionUuid());
        assertThat(response.getBody().getName()).isEqualTo(payload.getName());
    }

    /**
     * Scenario: POST persists tasks, logs, and results and ignores client uuids
     *   Given an activity body with one task that has a client uuid, one log, and one result, each with a client uuid and a generatedAt
     *   And an existing activity whose uuid equals that client activity uuid and that already has a task
     *   When the client POSTs /api/v2/pp/devops/activities
     *   Then the response is 201
     *   And the response uuid is not the client uuid
     *   And the nested task, log, and result uuids are not the client uuids
     *   And generatedAt on the log and the result equals the body
     *   And GET of the pre-existing activity still returns its original task
     */
    @Test
    public void whenCreateActivityWithClientUuidsThenNewRowsAndOriginalUntouched() {
        ActivityRes existingPayload = newActivity("dpv-existing", "existing-activity");
        TaskRes existingTask = new TaskRes();
        existingTask.setName("original-task");
        existingPayload.setTasks(List.of(existingTask));
        ActivityRes existing = createActivity(existingPayload);

        String clientActivityUuid = existing.getUuid();
        String clientTaskUuid = UUID.randomUUID().toString();
        String clientLogUuid = UUID.randomUUID().toString();
        String clientResultUuid = UUID.randomUUID().toString();
        Date generatedAt = new Date(1_700_000_000_000L);

        ActivityRes payload = newActivity("dpv-new", "new-activity");
        payload.setUuid(clientActivityUuid);

        TaskRes task = new TaskRes();
        task.setUuid(clientTaskUuid);
        task.setName("client-task");

        TaskLogRes log = new TaskLogRes();
        log.setUuid(clientLogUuid);
        log.setContent("log-content");
        log.setGeneratedAt(generatedAt);

        TaskResultRes result = new TaskResultRes();
        result.setUuid(clientResultUuid);
        result.setContent("result-content");
        result.setGeneratedAt(generatedAt);

        task.setLogs(List.of(log));
        task.setResults(List.of(result));
        payload.setTasks(List.of(task));

        ResponseEntity<ActivityRes> response = rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES),
                new HttpEntity<>(payload),
                ActivityRes.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUuid()).isNotEqualTo(clientActivityUuid);
        assertThat(response.getBody().getTasks()).hasSize(1);
        TaskRes createdTask = response.getBody().getTasks().get(0);
        assertThat(createdTask.getUuid()).isNotEqualTo(clientTaskUuid);
        assertThat(createdTask.getLogs()).hasSize(1);
        assertThat(createdTask.getLogs().get(0).getUuid()).isNotEqualTo(clientLogUuid);
        assertThat(createdTask.getLogs().get(0).getGeneratedAt()).isEqualTo(generatedAt);
        assertThat(createdTask.getResults()).hasSize(1);
        assertThat(createdTask.getResults().get(0).getUuid()).isNotEqualTo(clientResultUuid);
        assertThat(createdTask.getResults().get(0).getGeneratedAt()).isEqualTo(generatedAt);

        ResponseEntity<ActivityRes> existingGet = rest.getForEntity(
                apiUrl(RoutesV2.ACTIVITIES, "/" + existing.getUuid()),
                ActivityRes.class
        );
        assertThat(existingGet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(existingGet.getBody()).isNotNull();
        assertThat(existingGet.getBody().getTasks()).hasSize(1);
        assertThat(existingGet.getBody().getTasks().get(0).getName()).isEqualTo("original-task");
    }

    /**
     * Scenario: GET by id returns the task graph
     *   Given an activity that has two tasks, one with a log and a result
     *   When the client GETs /api/v2/pp/devops/activities/{uuid}
     *   Then the response is 200
     *   And both tasks are present with their logs and results
     */
    @Test
    public void whenGetActivityThenReturnTaskGraph() {
        ActivityRes payload = newActivity("dpv-get-graph", "get-graph");

        TaskRes taskWithChildren = new TaskRes();
        taskWithChildren.setName("task-with-children");
        TaskLogRes log = new TaskLogRes();
        log.setContent("log-content");
        TaskResultRes result = new TaskResultRes();
        result.setContent("result-content");
        taskWithChildren.setLogs(List.of(log));
        taskWithChildren.setResults(List.of(result));

        TaskRes secondTask = new TaskRes();
        secondTask.setName("second-task");

        payload.setTasks(List.of(taskWithChildren, secondTask));
        ActivityRes created = createActivity(payload);

        ResponseEntity<ActivityRes> response = rest.getForEntity(
                apiUrl(RoutesV2.ACTIVITIES, "/" + created.getUuid()),
                ActivityRes.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTasks()).hasSize(2);
        assertThat(response.getBody().getTasks())
                .anySatisfy(task -> {
                    assertThat(task.getName()).isEqualTo("task-with-children");
                    assertThat(task.getLogs()).hasSize(1);
                    assertThat(task.getLogs().get(0).getContent()).isEqualTo("log-content");
                    assertThat(task.getResults()).hasSize(1);
                    assertThat(task.getResults().get(0).getContent()).isEqualTo("result-content");
                });
        assertThat(response.getBody().getTasks())
                .anySatisfy(task -> assertThat(task.getName()).isEqualTo("second-task"));
    }

    /**
     * Scenario: Search filters by data product version uuid and does not return tasks
     *   Given two activities with different dataProductVersionUuid values, one of them with a task
     *   When the client GETs /api/v2/pp/devops/activities?dataProductVersionUuid={that uuid}
     *   Then the response is 200
     *   And the page contains only that activity
     *   And the tasks field is null or empty
     */
    @Test
    public void whenSearchByDataProductVersionUuidThenPageWithoutTasks() {
        ActivityRes withTask = newActivity("dpv-search-a", "search-a");
        TaskRes task = new TaskRes();
        task.setName("search-task");
        withTask.setTasks(List.of(task));
        ActivityRes createdWithTask = createActivity(withTask);
        createActivity(newActivity("dpv-search-b", "search-b"));

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                apiUrl(RoutesV2.ACTIVITIES, "?dataProductVersionUuid=" + createdWithTask.getDataProductVersionUuid()),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                }
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("uuid")).isEqualTo(createdWithTask.getUuid());
        Object tasks = content.get(0).get("tasks");
        assertThat(tasks == null || (tasks instanceof List<?> list && list.isEmpty())).isTrue();
    }

    /**
     * Scenario: PUT updates the activity and adds, updates, and deletes tasks as sent
     *   Given an activity with task A and task B
     *   When the client PUTs /api/v2/pp/devops/activities/{uuid} with task A changed, task B omitted, and a new task C with no uuid
     *   Then the response is 200
     *   And a following GET contains task A with the new values and task C
     *   And task B is absent
     *   And the activity itself still exists
     */
    @Test
    public void whenPutActivityThenReplaceTaskGraph() {
        ActivityRes payload = newActivity("dpv-put-graph", "put-graph");
        TaskRes taskA = new TaskRes();
        taskA.setName("task-a");
        TaskRes taskB = new TaskRes();
        taskB.setName("task-b");
        payload.setTasks(List.of(taskA, taskB));
        ActivityRes created = createActivity(payload);

        String taskAUuid = created.getTasks().stream()
                .filter(t -> "task-a".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getUuid();

        ActivityRes update = newActivity("dpv-put-graph", "put-graph-updated");
        update.setUuid(created.getUuid());

        TaskRes updatedA = new TaskRes();
        updatedA.setUuid(taskAUuid);
        updatedA.setName("task-a-updated");
        updatedA.setDescription("changed");

        TaskRes taskC = new TaskRes();
        taskC.setName("task-c");

        update.setTasks(List.of(updatedA, taskC));

        ResponseEntity<ActivityRes> putResponse = rest.exchange(
                apiUrl(RoutesV2.ACTIVITIES, "/" + created.getUuid()),
                HttpMethod.PUT,
                new HttpEntity<>(update),
                ActivityRes.class
        );
        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ActivityRes> getResponse = rest.getForEntity(
                apiUrl(RoutesV2.ACTIVITIES, "/" + created.getUuid()),
                ActivityRes.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().getName()).isEqualTo("put-graph-updated");
        assertThat(getResponse.getBody().getTasks()).hasSize(2);
        assertThat(getResponse.getBody().getTasks())
                .anySatisfy(task -> {
                    assertThat(task.getUuid()).isEqualTo(taskAUuid);
                    assertThat(task.getName()).isEqualTo("task-a-updated");
                    assertThat(task.getDescription()).isEqualTo("changed");
                });
        assertThat(getResponse.getBody().getTasks())
                .anySatisfy(task -> assertThat(task.getName()).isEqualTo("task-c"));
        assertThat(getResponse.getBody().getTasks())
                .noneMatch(task -> "task-b".equals(task.getName()));
    }

    /**
     * Scenario: DELETE removes the activity and its tasks, logs, and results
     *   Given an activity with a task that has a log and a result
     *   When the client DELETEs /api/v2/pp/devops/activities/{uuid}
     *   Then the response is 204
     *   And a following GET of that uuid is 404
     *   And no rows remain in activities_tasks, activities_task_logs, or activities_task_results for that activity's former children
     */
    @Test
    public void whenDeleteActivityThenCascade() {
        ActivityRes payload = newActivity("dpv-delete", "delete-activity");
        TaskRes task = new TaskRes();
        task.setName("to-delete");
        TaskLogRes log = new TaskLogRes();
        log.setContent("log");
        TaskResultRes result = new TaskResultRes();
        result.setContent("result");
        task.setLogs(List.of(log));
        task.setResults(List.of(result));
        payload.setTasks(List.of(task));
        ActivityRes created = createActivity(payload);

        String taskUuid = created.getTasks().get(0).getUuid();
        String logUuid = created.getTasks().get(0).getLogs().get(0).getUuid();
        String resultUuid = created.getTasks().get(0).getResults().get(0).getUuid();

        ResponseEntity<Void> deleteResponse = rest.exchange(
                apiUrl(RoutesV2.ACTIVITIES, "/" + created.getUuid()),
                HttpMethod.DELETE,
                null,
                Void.class
        );
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<JsonNode> getResponse = rest.getForEntity(
                apiUrl(RoutesV2.ACTIVITIES, "/" + created.getUuid()),
                JsonNode.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(countRows("activities_tasks", "uuid", taskUuid)).isZero();
        assertThat(countRows("activities_task_logs", "uuid", logUuid)).isZero();
        assertThat(countRows("activities_task_results", "uuid", resultUuid)).isZero();
        assertThat(countRows("activities_tasks", "activity_uuid", created.getUuid())).isZero();
    }

    /**
     * Scenario: Missing required fields are rejected
     *   Given an activity body with a blank name or a blank dataProductVersionUuid
     *   When the client POSTs /api/v2/pp/devops/activities
     *   Then the response is 400
     *   And the error message is "Name is required" or "Data product version UUID is required"
     */
    @Test
    public void whenCreateActivityWithoutRequiredFieldsThenBadRequest() {
        ActivityRes blankName = newActivity("dpv-required", " ");
        ResponseEntity<ErrorRes> blankNameResponse = rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES),
                new HttpEntity<>(blankName),
                ErrorRes.class
        );
        assertThat(blankNameResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blankNameResponse.getBody()).isNotNull();
        assertThat(blankNameResponse.getBody().getMessage()).isEqualTo("Name is required");

        ActivityRes blankDpv = new ActivityRes();
        blankDpv.setDataProductVersionUuid(" ");
        blankDpv.setName("has-name");
        ResponseEntity<ErrorRes> blankDpvResponse = rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES),
                new HttpEntity<>(blankDpv),
                ErrorRes.class
        );
        assertThat(blankDpvResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blankDpvResponse.getBody()).isNotNull();
        assertThat(blankDpvResponse.getBody().getMessage()).isEqualTo("Data product version UUID is required");
    }

    /**
     * Scenario: An unknown status is rejected
     *   Given an activity body whose status is not in the closed set
     *   When the client POSTs /api/v2/pp/devops/activities
     *   Then the response is 400
     *   And the error body includes a message
     */
    @Test
    public void whenCreateActivityWithUnknownStatusThenBadRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(
                "{\"dataProductVersionUuid\":\"dpv-unknown-status\",\"name\":\"unknown-status\",\"status\":\"PLANNED\"}",
                headers
        );

        ResponseEntity<JsonNode> response = rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES),
                entity,
                JsonNode.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        String message = firstNonBlank(
                textOrNull(response.getBody(), "message"),
                textOrNull(response.getBody(), "detail"),
                textOrNull(response.getBody(), "title")
        );
        assertThat(message).isNotBlank();
    }

    /**
     * Scenario: Oversize log content is rejected
     *   Given an activity body with a log whose content exceeds 1048576 characters
     *   When the client POSTs /api/v2/pp/devops/activities
     *   Then the response is 400
     *   And the error message is "Log content cannot exceed 1048576 characters"
     */
    @Test
    public void whenCreateActivityWithOversizeLogContentThenBadRequest() {
        ActivityRes payload = newActivity("dpv-oversize-log", "oversize-log");
        TaskRes task = new TaskRes();
        task.setName("task");
        TaskLogRes log = new TaskLogRes();
        log.setContent("x".repeat(1_048_577));
        task.setLogs(List.of(log));
        payload.setTasks(List.of(task));

        ResponseEntity<ErrorRes> response = rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES),
                new HttpEntity<>(payload),
                ErrorRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Log content cannot exceed 1048576 characters");
    }

    /**
     * Scenario: Oversize task description is rejected
     *   Given an activity body with a task whose description exceeds 10000 characters
     *   When the client POSTs /api/v2/pp/devops/activities
     *   Then the response is 400
     *   And the error message is "Task description cannot exceed 10000 characters"
     */
    @Test
    public void whenCreateActivityWithOversizeTaskDescriptionThenBadRequest() {
        ActivityRes payload = newActivity("dpv-oversize-desc", "oversize-desc");
        TaskRes task = new TaskRes();
        task.setName("task");
        task.setDescription("d".repeat(10_001));
        payload.setTasks(List.of(task));

        ResponseEntity<ErrorRes> response = rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES),
                new HttpEntity<>(payload),
                ErrorRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Task description cannot exceed 10000 characters");
    }

    /**
     * Scenario: Duplicate task uuid is rejected
     *   Given an activity body with two tasks that share the same non-null uuid
     *   When the client POSTs /api/v2/pp/devops/activities
     *   Then the response is 400
     *   And the error message is "Duplicate task uuid in activity"
     */
    @Test
    public void whenCreateActivityWithDuplicateTaskUuidThenBadRequest() {
        String sharedUuid = UUID.randomUUID().toString();
        ActivityRes payload = newActivity("dpv-dup-task-uuid", "dup-task-uuid");
        TaskRes first = new TaskRes();
        first.setUuid(sharedUuid);
        first.setName("first");
        TaskRes second = new TaskRes();
        second.setUuid(sharedUuid);
        second.setName("second");
        payload.setTasks(List.of(first, second));

        ResponseEntity<ErrorRes> response = rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES),
                new HttpEntity<>(payload),
                ErrorRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Duplicate task uuid in activity");
    }

    /**
     * Scenario: Duplicate log uuid is rejected
     *   Given an activity body with one task that has two logs that share the same non-null uuid
     *   When the client POSTs /api/v2/pp/devops/activities
     *   Then the response is 400
     *   And the error message is "Duplicate log uuid in task"
     */
    @Test
    public void whenCreateActivityWithDuplicateLogUuidThenBadRequest() {
        String sharedUuid = UUID.randomUUID().toString();
        ActivityRes payload = newActivity("dpv-dup-log-uuid", "dup-log-uuid");
        TaskRes task = new TaskRes();
        task.setName("task");
        TaskLogRes first = new TaskLogRes();
        first.setUuid(sharedUuid);
        first.setContent("log-1");
        TaskLogRes second = new TaskLogRes();
        second.setUuid(sharedUuid);
        second.setContent("log-2");
        task.setLogs(List.of(first, second));
        payload.setTasks(List.of(task));

        ResponseEntity<ErrorRes> response = rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES),
                new HttpEntity<>(payload),
                ErrorRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Duplicate log uuid in task");
    }

    /**
     * Scenario: Duplicate result uuid is rejected
     *   Given an activity body with one task that has two results that share the same non-null uuid
     *   When the client POSTs /api/v2/pp/devops/activities
     *   Then the response is 400
     *   And the error message is "Duplicate result uuid in task"
     */
    @Test
    public void whenCreateActivityWithDuplicateResultUuidThenBadRequest() {
        String sharedUuid = UUID.randomUUID().toString();
        ActivityRes payload = newActivity("dpv-dup-result-uuid", "dup-result-uuid");
        TaskRes task = new TaskRes();
        task.setName("task");
        TaskResultRes first = new TaskResultRes();
        first.setUuid(sharedUuid);
        first.setContent("result-1");
        TaskResultRes second = new TaskResultRes();
        second.setUuid(sharedUuid);
        second.setContent("result-2");
        task.setResults(List.of(first, second));
        payload.setTasks(List.of(task));

        ResponseEntity<ErrorRes> response = rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES),
                new HttpEntity<>(payload),
                ErrorRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Duplicate result uuid in task");
    }

    /**
     * Scenario: Unknown uuid is not found
     *   Given no activity with a given uuid
     *   When the client GETs or PUTs or DELETEs /api/v2/pp/devops/activities/{uuid}
     *   Then the response is 404
     */
    @Test
    public void whenActivityUuidUnknownThenNotFound() {
        String unknownUuid = UUID.randomUUID().toString();

        assertThat(rest.getForEntity(apiUrl(RoutesV2.ACTIVITIES, "/" + unknownUuid), JsonNode.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ActivityRes payload = newActivity("dpv-missing", "missing");
        assertThat(rest.exchange(
                apiUrl(RoutesV2.ACTIVITIES, "/" + unknownUuid),
                HttpMethod.PUT,
                new HttpEntity<>(payload),
                JsonNode.class
        ).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(rest.exchange(
                apiUrl(RoutesV2.ACTIVITIES, "/" + unknownUuid),
                HttpMethod.DELETE,
                null,
                Void.class
        ).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Scenario: An empty search returns an empty page
     *   Given no activities
     *   When the client GETs /api/v2/pp/devops/activities
     *   Then the response is 200
     *   And the page is empty
     */
    @Test
    public void whenSearchWithNoRowsThenEmptyPage() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                apiUrl(RoutesV2.ACTIVITIES),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                }
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("content")).isInstanceOf(List.class);
        assertThat((List<?>) response.getBody().get("content")).isEmpty();
    }

    /**
     * Scenario: An unknown sort property is rejected
     *   Given no special precondition
     *   When the client GETs /api/v2/pp/devops/activities?sort=notAField,asc
     *   Then the response is 400
     */
    @Test
    public void whenSearchWithInvalidSortThenBadRequest() {
        ResponseEntity<JsonNode> response = rest.getForEntity(
                apiUrl(RoutesV2.ACTIVITIES, "?sort=notAField,asc"),
                JsonNode.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Scenario: Duplicate activities are allowed
     *   Given an existing PENDING activity for a data product version and name
     *   When the client POSTs another activity with the same dataProductVersionUuid, name, and status PENDING
     *   Then the response is 201
     */
    @Test
    public void whenCreateDuplicatePendingActivityThenCreated() {
        ActivityRes first = newActivity("dpv-dup", "same-name");
        first.setStatus(ExecutionStatus.PENDING);
        createActivity(first);

        ActivityRes second = newActivity("dpv-dup", "same-name");
        second.setStatus(ExecutionStatus.PENDING);
        ResponseEntity<ActivityRes> response = rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES),
                new HttpEntity<>(second),
                ActivityRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /**
     * Scenario: A non-terminal activity can be deleted
     *   Given an activity whose status is RUNNING
     *   When the client DELETEs /api/v2/pp/devops/activities/{uuid}
     *   Then the response is 204
     */
    @Test
    public void whenDeleteRunningActivityThenNoContent() {
        ActivityRes payload = newActivity("dpv-running-delete", "running-delete");
        payload.setStatus(ExecutionStatus.RUNNING);
        ActivityRes created = createActivity(payload);

        ResponseEntity<Void> response = rest.exchange(
                apiUrl(RoutesV2.ACTIVITIES, "/" + created.getUuid()),
                HttpMethod.DELETE,
                null,
                Void.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /**
     * Scenario: An empty task list clears tasks
     *   Given an activity that has a task
     *   When the client PUTs /api/v2/pp/devops/activities/{uuid} with tasks equal to an empty list
     *   Then the response is 200
     *   And a following GET has no tasks
     *   And the activity still exists
     */
    @Test
    public void whenPutActivityWithEmptyTasksThenTasksRemoved() {
        ActivityRes payload = newActivity("dpv-clear-tasks", "clear-tasks");
        TaskRes task = new TaskRes();
        task.setName("to-clear");
        payload.setTasks(List.of(task));
        ActivityRes created = createActivity(payload);

        ActivityRes update = newActivity("dpv-clear-tasks", "clear-tasks");
        update.setUuid(created.getUuid());
        update.setTasks(new ArrayList<>());

        ResponseEntity<ActivityRes> putResponse = rest.exchange(
                apiUrl(RoutesV2.ACTIVITIES, "/" + created.getUuid()),
                HttpMethod.PUT,
                new HttpEntity<>(update),
                ActivityRes.class
        );
        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ActivityRes> getResponse = rest.getForEntity(
                apiUrl(RoutesV2.ACTIVITIES, "/" + created.getUuid()),
                ActivityRes.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().getTasks() == null || getResponse.getBody().getTasks().isEmpty()).isTrue();
    }

    /**
     * Scenario: A task uuid from another activity does not move that task
     *   Given activity A with task T and activity B
     *   When the client PUTs activity B with a task whose uuid is T
     *   Then the response is 200
     *   And GET activity A still returns task T
     *   And GET activity B does not return uuid T
     */
    @Test
    public void whenPutForeignTaskUuidThenDoNotReparent() {
        ActivityRes activityAPayload = newActivity("dpv-a", "activity-a");
        TaskRes taskT = new TaskRes();
        taskT.setName("task-t");
        activityAPayload.setTasks(List.of(taskT));
        ActivityRes activityA = createActivity(activityAPayload);
        String taskTUuid = activityA.getTasks().get(0).getUuid();

        ActivityRes activityB = createActivity(newActivity("dpv-b", "activity-b"));

        ActivityRes updateB = newActivity("dpv-b", "activity-b");
        updateB.setUuid(activityB.getUuid());
        TaskRes foreignTask = new TaskRes();
        foreignTask.setUuid(taskTUuid);
        foreignTask.setName("attempted-move");
        updateB.setTasks(List.of(foreignTask));

        ResponseEntity<ActivityRes> putResponse = rest.exchange(
                apiUrl(RoutesV2.ACTIVITIES, "/" + activityB.getUuid()),
                HttpMethod.PUT,
                new HttpEntity<>(updateB),
                ActivityRes.class
        );
        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ActivityRes> getA = rest.getForEntity(
                apiUrl(RoutesV2.ACTIVITIES, "/" + activityA.getUuid()),
                ActivityRes.class
        );
        assertThat(getA.getBody()).isNotNull();
        assertThat(getA.getBody().getTasks()).hasSize(1);
        assertThat(getA.getBody().getTasks().get(0).getUuid()).isEqualTo(taskTUuid);

        ResponseEntity<ActivityRes> getB = rest.getForEntity(
                apiUrl(RoutesV2.ACTIVITIES, "/" + activityB.getUuid()),
                ActivityRes.class
        );
        assertThat(getB.getBody()).isNotNull();
        assertThat(getB.getBody().getTasks()).hasSize(1);
        assertThat(getB.getBody().getTasks().get(0).getUuid()).isNotEqualTo(taskTUuid);
        assertThat(getB.getBody().getTasks().get(0).getName()).isEqualTo("attempted-move");
    }

    /**
     * Scenario: Two tasks may share a name
     *   Given an activity body with two tasks with the same name and a null providerRunId
     *   When the client POSTs /api/v2/pp/devops/activities
     *   Then the response is 201
     *   And GET returns both tasks
     */
    @Test
    public void whenCreateTwoTasksWithSameNameThenBothStored() {
        ActivityRes payload = newActivity("dpv-same-task-name", "same-task-name");
        TaskRes first = new TaskRes();
        first.setName("shared-name");
        TaskRes second = new TaskRes();
        second.setName("shared-name");
        payload.setTasks(List.of(first, second));

        ResponseEntity<ActivityRes> response = rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES),
                new HttpEntity<>(payload),
                ActivityRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();

        ResponseEntity<ActivityRes> getResponse = rest.getForEntity(
                apiUrl(RoutesV2.ACTIVITIES, "/" + response.getBody().getUuid()),
                ActivityRes.class
        );
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().getTasks()).hasSize(2);
        assertThat(getResponse.getBody().getTasks())
                .allSatisfy(task -> {
                    assertThat(task.getName()).isEqualTo("shared-name");
                    assertThat(task.getProviderRunId()).isNull();
                });
    }

    /**
     * Scenario: Whitespace-padded task uuid on PUT still updates the owned task
     *   Given an activity with task T
     *   When the client PUTs that activity with task T's uuid padded with spaces and a changed name
     *   Then the response is 200
     *   And GET returns the same task uuid T with the new name
     */
    @Test
    public void whenPutActivityWithPaddedTaskUuidThenUpdateOwnedTask() {
        ActivityRes payload = newActivity("dpv-padded-uuid", "padded-uuid");
        TaskRes task = new TaskRes();
        task.setName("original-name");
        payload.setTasks(List.of(task));
        ActivityRes created = createActivity(payload);
        String taskUuid = created.getTasks().get(0).getUuid();

        ActivityRes update = newActivity("dpv-padded-uuid", "padded-uuid");
        update.setUuid(created.getUuid());
        TaskRes paddedTask = new TaskRes();
        paddedTask.setUuid("  " + taskUuid + "  ");
        paddedTask.setName("updated-name");
        update.setTasks(List.of(paddedTask));

        ResponseEntity<ActivityRes> putResponse = rest.exchange(
                apiUrl(RoutesV2.ACTIVITIES, "/" + created.getUuid()),
                HttpMethod.PUT,
                new HttpEntity<>(update),
                ActivityRes.class
        );
        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ActivityRes> getResponse = rest.getForEntity(
                apiUrl(RoutesV2.ACTIVITIES, "/" + created.getUuid()),
                ActivityRes.class
        );
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().getTasks()).hasSize(1);
        assertThat(getResponse.getBody().getTasks().get(0).getUuid()).isEqualTo(taskUuid);
        assertThat(getResponse.getBody().getTasks().get(0).getName()).isEqualTo("updated-name");
    }

    /**
     * Scenario: There is no task collection
     *   Given an existing activity
     *   When the client GETs /api/v2/pp/devops/activities/{uuid}/tasks
     *   Then the response is 404
     */
    @Test
    public void whenGetTaskCollectionThenNotFound() {
        ActivityRes created = createActivity(newActivity("dpv-no-tasks-route", "no-tasks-route"));

        ResponseEntity<JsonNode> response = rest.getForEntity(
                apiUrl(RoutesV2.ACTIVITIES, "/" + created.getUuid() + "/tasks"),
                JsonNode.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ActivityRes createActivity(ActivityRes payload) {
        ResponseEntity<ActivityRes> response = rest.postForEntity(
                apiUrl(RoutesV2.ACTIVITIES),
                new HttpEntity<>(payload),
                ActivityRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private static ActivityRes newActivity(String dataProductVersionUuid, String name) {
        ActivityRes activity = new ActivityRes();
        activity.setDataProductVersionUuid(dataProductVersionUuid);
        activity.setName(name);
        return activity;
    }

    private int countRows(String table, String column, String value) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + schema + "." + table + " where " + column + " = ?",
                Integer.class,
                value
        );
        return count == null ? 0 : count;
    }

    private static String textOrNull(JsonNode body, String field) {
        JsonNode node = body.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
