package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskstatus;

import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Record Task Status with hand-written ports: how the task is addressed and when the activity advances.
 * Traces to BDMD-5443 and {@code spdd/prompt/BDMD-5443-202610291208-[Feat]-api-task-address-by-provider-run-id.md}.
 */
class RecordTaskStatusTest {

    /**
     * Feature: Address a running task by provider run id
     *
     * Scenario: Instrumented task closed by its run id
     *   Given an instrumented task is RUNNING with provider run id "gh-123"
     *   When the CLI records logs, results and status SUCCEEDED with only the provider run id
     *   Then the task is SUCCEEDED with the log and result stored
     *   And the activity is advanced
     */
    @Test
    void whenOnlyRunIdGivenThenStatusRecordedAndActivityAdvancedOnce() {
        Activity activity = activity("activity-1", "dp-1", running("deploy", "gh-123"));
        Activity other = activity("activity-2", "dp-2", running("deploy", "gh-999"));
        FakePersistence persistence = new FakePersistence(activity, other);
        List<String> advanced = new ArrayList<>();
        List<Activity> presented = new ArrayList<>();

        run(command("gh-123", null, null, null, null, ExecutionStatus.SUCCEEDED), persistence, advanced, presented);

        assertThat(taskOf(activity, "deploy").getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(taskOf(activity, "deploy").getFinishedAt()).isNotNull();
        assertThat(taskOf(other, "deploy").getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(advanced).containsExactly("activity-1");
        assertThat(presented).containsExactly(activity);
    }

    /**
     * Feature: Address a running task by provider run id
     *
     * Scenario: No running task with the run id
     *   Given no task is RUNNING with provider run id "unknown"
     *   When the CLI records results with that provider run id
     *   Then the response is 404 "No RUNNING task with provider run id unknown"
     */
    @Test
    void whenNoRunningTaskWithRunIdThenNotFoundAndNothingAdvanced() {
        FakePersistence persistence = new FakePersistence(activity("activity-1", "dp-1", running("deploy", "gh-123")));
        List<String> advanced = new ArrayList<>();

        assertThatThrownBy(() -> run(command("unknown", null, null, null, null, ExecutionStatus.SUCCEEDED),
                persistence, advanced, new ArrayList<>()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No RUNNING task with provider run id unknown");
        assertThat(advanced).isEmpty();
    }

    /**
     * Feature: Address a running task by provider run id
     *
     * Scenario: Run id shared by two running tasks is refused
     *   Given two activities each have a RUNNING task with provider run id "gh-123"
     *   When the CLI records the status with that provider run id
     *   Then the response is 400 naming both tasks and activities
     *   And no task changes
     */
    @Test
    void whenRunIdSharedByTwoRunningTasksThenBadRequestAndNoTaskChanges() {
        Activity first = activity("activity-1", "dp-1", running("deploy", "gh-123"));
        Activity second = activity("activity-2", "dp-2", running("deploy", "gh-123"));
        FakePersistence persistence = new FakePersistence(first, second);
        List<String> advanced = new ArrayList<>();

        assertThatThrownBy(() -> run(command("gh-123", null, null, null, null, ExecutionStatus.SUCCEEDED),
                persistence, advanced, new ArrayList<>()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Provider run id gh-123 matches more than one RUNNING task: "
                        + "task deploy of activity activity-1, task deploy of activity activity-2");
        assertThat(taskOf(first, "deploy").getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(taskOf(second, "deploy").getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(advanced).isEmpty();
    }

    /**
     * Feature: Address a running task by provider run id
     *
     * Scenario: Run id of a finished task is not matched
     *   Given a task of an earlier activity is SUCCEEDED with provider run id "gh-123"
     *   And a task of a new activity is RUNNING with provider run id "gh-123"
     *   When the CLI records the status with that provider run id
     *   Then the status is stored on the RUNNING task only
     *
     * Same activity: "build" is SUCCEEDED and "deploy" is RUNNING, both with run id "gh-123".
     * The fake returns the activity, as the real query does, so only the use case's per-task RUNNING filter separates them.
     */
    @Test
    void whenSameActivityHasFinishedAndRunningTaskWithRunIdThenOnlyRunningTaskClosed() {
        Activity activity = activity("activity-1", "dp-1",
                task("build", ExecutionStatus.SUCCEEDED, "gh-123", "cli"), running("deploy", "gh-123"));
        FakePersistence persistence = new FakePersistence(activity);
        List<String> advanced = new ArrayList<>();

        run(command("gh-123", null, null, null, null, ExecutionStatus.FAILED), persistence, advanced, new ArrayList<>());

        assertThat(taskOf(activity, "deploy").getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(taskOf(activity, "build").getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(advanced).containsExactly("activity-1");
    }

    /**
     * Feature: Address a running task by provider run id
     *
     * Scenario: No address is refused
     *   When the CLI records the status with no provider run id and no natural key
     *   Then the response is 400 naming the accepted addresses
     */
    @Test
    void whenNoAddressThenBadRequest() {
        FakePersistence persistence = new FakePersistence();

        assertThatThrownBy(() -> run(command(null, null, null, null, "deploy", ExecutionStatus.SUCCEEDED),
                persistence, new ArrayList<>(), new ArrayList<>()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Provider run id or data product FQN, version tag, and activity name are required");
        assertThatThrownBy(() -> run(command("gh-123", "dp-1", "1.0.0", "prod", "deploy", ExecutionStatus.SUCCEEDED),
                persistence, new ArrayList<>(), new ArrayList<>()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Send only one task address: provider run id, or data product FQN, version tag, and activity name");
    }

    /**
     * Feature: Address a running task by provider run id
     *
     * Scenario: A second status by run id finds no running task
     *   Given an instrumented task was closed by its run id
     *   When the CLI records the status again with the same provider run id
     *   Then the response is 404
     */
    @Test
    void whenSecondStatusByRunIdThenNotFound() {
        Activity activity = activity("activity-1", "dp-1", running("deploy", "gh-123"));
        FakePersistence persistence = new FakePersistence(activity);
        List<String> advanced = new ArrayList<>();
        run(command("gh-123", null, null, null, null, ExecutionStatus.SUCCEEDED), persistence, advanced, new ArrayList<>());

        assertThatThrownBy(() -> run(command("gh-123", null, null, null, null, ExecutionStatus.FAILED),
                persistence, advanced, new ArrayList<>()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No RUNNING task with provider run id gh-123");
        assertThat(taskOf(activity, "deploy").getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(advanced).containsExactly("activity-1");
    }

    /**
     * Feature: Address a running task by provider run id
     *
     * Scenario: Existing natural-key address still works
     *   Given an instrumented task is RUNNING
     *   When the CLI records the status with the data product, version tag, activity and task name
     *   Then the response is 200 as before
     */
    @Test
    void whenNaturalKeyThenRecordsStatusAsBefore() {
        Activity activity = activity("activity-1", "dp-1", running("deploy", null));
        FakePersistence persistence = new FakePersistence(activity);
        List<String> advanced = new ArrayList<>();

        run(command(null, "dp-1", "1.0.0", "prod", "deploy", ExecutionStatus.FAILED), persistence, advanced, new ArrayList<>());

        assertThat(taskOf(activity, "deploy").getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(advanced).containsExactly("activity-1");
        assertThatThrownBy(() -> run(command(null, "dp-1", "1.0.0", "prod", null, ExecutionStatus.FAILED),
                persistence, advanced, new ArrayList<>()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Task name is required");
    }

    private static void run(RecordTaskStatusCommand command, FakePersistence persistence,
                            List<String> advanced, List<Activity> presented) {
        new RecordTaskStatus(command, presented::add, persistence,
                executorName -> ExecutionMode.INSTRUMENTED, advanced::add, new DirectTransactions()).execute();
    }

    private static RecordTaskStatusCommand command(String providerRunId, String fqn, String versionTag,
                                                   String activityName, String taskName, ExecutionStatus status) {
        return new RecordTaskStatusCommand(providerRunId, fqn, versionTag, activityName, taskName, status);
    }

    private static Activity activity(String uuid, String fqn, Task... tasks) {
        Activity activity = new Activity();
        activity.setUuid(uuid);
        activity.setDataProductFqn(fqn);
        activity.setDataProductVersionTag("1.0.0");
        activity.setName("prod");
        activity.setStatus(ExecutionStatus.RUNNING);
        activity.setTasks(new ArrayList<>(List.of(tasks)));
        for (Task task : tasks) {
            task.setActivity(activity);
        }
        return activity;
    }

    private static Task task(String name, ExecutionStatus status, String providerRunId, String executorName) {
        Task task = new Task();
        task.setUuid("task-" + name);
        task.setName(name);
        task.setStatus(status);
        task.setProviderRunId(providerRunId);
        task.setExecutorName(executorName);
        return task;
    }

    private static Task running(String name, String providerRunId) {
        return task(name, ExecutionStatus.RUNNING, providerRunId, "cli");
    }

    private static Task taskOf(Activity activity, String name) {
        return activity.getTasks().stream().filter(task -> name.equals(task.getName())).findFirst().orElseThrow();
    }

    /**
     * Stored activities. The run-id query returns the activities with a RUNNING task of that run id, as the port does.
     */
    private static final class FakePersistence implements RecordTaskStatusPersistenceOutboundPort {

        private final List<Activity> stored;
        private final List<Activity> saved = new ArrayList<>();

        FakePersistence(Activity... activities) {
            this.stored = List.of(activities);
        }

        @Override
        public List<Activity> findOpenActivities(String dataProductFqn, String dataProductVersionTag, String activityName) {
            return stored.stream()
                    .filter(activity -> dataProductFqn.equals(activity.getDataProductFqn())
                            && dataProductVersionTag.equals(activity.getDataProductVersionTag())
                            && activityName.equals(activity.getName()))
                    .toList();
        }

        @Override
        public List<Activity> findActivitiesWithRunningTask(String providerRunId) {
            return stored.stream()
                    .filter(activity -> activity.getTasks().stream().anyMatch(task ->
                            task.getStatus() == ExecutionStatus.RUNNING && providerRunId.equals(task.getProviderRunId())))
                    .toList();
        }

        @Override
        public Activity findForUpdate(String uuid) {
            return stored.stream().filter(activity -> uuid.equals(activity.getUuid())).findFirst().orElseThrow();
        }

        @Override
        public Activity findActivity(String uuid) {
            return findForUpdate(uuid);
        }

        @Override
        public Activity save(Activity activity) {
            saved.add(activity);
            return activity;
        }
    }

    private static final class DirectTransactions implements TransactionalOutboundPort {

        @Override
        public void doInTransaction(Runnable runnable) {
            runnable.run();
        }

        @Override
        public <T, R> R doInTransactionWithResults(Function<T, R> function, T arg) {
            return function.apply(arg);
        }
    }
}
