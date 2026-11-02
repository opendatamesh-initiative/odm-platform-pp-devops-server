package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskresults;

import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskResult;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Record Task Results with hand-written ports: how the task is addressed.
 * Traces to BDMD-5443 and {@code spdd/prompt/BDMD-5443-202610291208-[Feat]-api-task-address-by-provider-run-id.md}.
 */
class RecordTaskResultsTest {

    private static final String NO_ADDRESS =
            "Provider run id, activity UUID, or data product FQN, version tag, and activity name are required";
    private static final String MIXED_ADDRESS =
            "Send only one task address: provider run id, activity UUID, or data product FQN, version tag, and activity name";

    /**
     * Feature: Address a running task by provider run id
     *
     * Scenario: Task name narrows a shared run id
     *   Given two activities each have a RUNNING task with provider run id "gh-123", named "build" and "deploy"
     *   When the CLI records results with that provider run id and task name "deploy"
     *   Then the result is stored on the "deploy" task
     */
    @Test
    void whenRunIdSharedAndTaskNameGivenThenRecordsOnNamedTask() {
        Activity first = activity("activity-1", "dp-1", running("build", "gh-123"));
        Activity second = activity("activity-2", "dp-2", running("deploy", "gh-123"));
        FakePersistence persistence = new FakePersistence(first, second);
        List<Activity> presented = new ArrayList<>();

        run(command("gh-123", null, null, null, null, "deploy"), persistence, presented);

        assertThat(persistence.saved).containsExactly(second);
        assertThat(taskOf(second, "deploy").getResults()).extracting(TaskResult::getContent).containsExactly("result");
        assertThat(taskOf(first, "build").getResults()).isNullOrEmpty();
        assertThat(presented).containsExactly(second);
    }

    /**
     * Feature: Address a running task by provider run id
     *
     * Scenario: Instrumented task closed by its run id
     *   Given an instrumented task is RUNNING with provider run id "gh-123"
     *   When the CLI records logs, results and status SUCCEEDED with only the provider run id
     *   Then each response is 200
     */
    @Test
    void whenOnlyRunIdGivenThenRecordsOnTheRunningTask() {
        Activity activity = activity("activity-1", "dp-1", running("deploy", "gh-123"));
        FakePersistence persistence = new FakePersistence(activity);

        run(command("  gh-123 ", null, null, null, null, null), persistence, new ArrayList<>());

        assertThat(taskOf(activity, "deploy").getResults()).extracting(TaskResult::getContent).containsExactly("result");
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
    void whenNoRunningTaskWithRunIdThenNotFound() {
        FakePersistence persistence = new FakePersistence(activity("activity-1", "dp-1", running("deploy", "gh-123")));

        assertThatThrownBy(() -> run(command("unknown", null, null, null, null, null), persistence, new ArrayList<>()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No RUNNING task with provider run id unknown");
        assertThatThrownBy(() -> run(command("gh-123", null, null, null, null, "other"), persistence, new ArrayList<>()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No RUNNING task with provider run id gh-123 and task name other");
        assertThat(persistence.saved).isEmpty();
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
    void whenRunIdSharedByTwoRunningTasksThenBadRequestNamingBoth() {
        FakePersistence persistence = new FakePersistence(
                activity("activity-1", "dp-1", running("deploy", "gh-123")),
                activity("activity-2", "dp-2", running("deploy", "gh-123")));

        assertThatThrownBy(() -> run(command("gh-123", null, null, null, null, null), persistence, new ArrayList<>()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Provider run id gh-123 matches more than one RUNNING task: "
                        + "task deploy of activity activity-1, task deploy of activity activity-2");
        assertThat(persistence.saved).isEmpty();
    }

    /**
     * Feature: Address a running task by provider run id
     *
     * Scenario: Run id of a finished task is not matched
     *   Given a task of an earlier activity is SUCCEEDED with provider run id "gh-123"
     *   And a task of a new activity is RUNNING with provider run id "gh-123"
     *   When the CLI records results with that provider run id
     *   Then the result is stored on the RUNNING task only
     */
    @Test
    void whenEarlierTaskSucceededWithSameRunIdThenOnlyRunningTaskMatched() {
        Activity earlier = activity("activity-1", "dp-1", task("deploy", ExecutionStatus.SUCCEEDED, "gh-123", "cli"));
        Activity current = activity("activity-2", "dp-2", running("deploy", "gh-123"));
        FakePersistence persistence = new FakePersistence(earlier, current);

        run(command("gh-123", null, null, null, null, null), persistence, new ArrayList<>());

        assertThat(persistence.saved).containsExactly(current);
        assertThat(taskOf(earlier, "deploy").getResults()).isNullOrEmpty();
    }

    /**
     * Feature: Address a running task by provider run id
     *
     * Scenario: Run id of a finished task is not matched
     *   Given a task of an earlier activity is SUCCEEDED with provider run id "gh-123"
     *   And a task of a new activity is RUNNING with provider run id "gh-123"
     *   When the CLI records results with that provider run id
     *   Then the result is stored on the RUNNING task only
     *
     * Same activity: "build" is SUCCEEDED and "deploy" is RUNNING, both with run id "gh-123".
     * The fake returns the activity, as the real query does, so only the use case's per-task RUNNING filter separates them.
     */
    @Test
    void whenSameActivityHasFinishedAndRunningTaskWithRunIdThenOnlyRunningTaskWritten() {
        Activity activity = activity("activity-1", "dp-1",
                task("build", ExecutionStatus.SUCCEEDED, "gh-123", "cli"), running("deploy", "gh-123"));
        FakePersistence persistence = new FakePersistence(activity);

        run(command("gh-123", null, null, null, null, null), persistence, new ArrayList<>());

        assertThat(taskOf(activity, "deploy").getResults()).extracting(TaskResult::getContent).containsExactly("result");
        assertThat(taskOf(activity, "build").getResults()).isNullOrEmpty();
    }

    /**
     * Feature: Address a running task by provider run id
     *
     * Scenario: Mixed addresses are refused
     *   When the CLI records results with a provider run id and an activity uuid
     *   Then the response is 400 asking for only one task address
     */
    @Test
    void whenRunIdAndActivityUuidThenBadRequest() {
        FakePersistence persistence = new FakePersistence(activity("activity-1", "dp-1", running("deploy", "gh-123")));

        assertThatThrownBy(() -> run(command("gh-123", "activity-1", null, null, null, "deploy"), persistence, new ArrayList<>()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(MIXED_ADDRESS);
        assertThatThrownBy(() -> run(command("gh-123", null, "dp-1", "1.0.0", "prod", "deploy"), persistence, new ArrayList<>()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(MIXED_ADDRESS);
        assertThat(persistence.saved).isEmpty();
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

        assertThatThrownBy(() -> run(command(" ", null, null, null, null, "deploy"), persistence, new ArrayList<>()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(NO_ADDRESS);
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
    void whenNaturalKeyOrActivityUuidThenRecordsAsBefore() {
        Activity activity = activity("activity-1", "dp-1", running("deploy", null));
        FakePersistence persistence = new FakePersistence(activity);

        run(command(null, null, "dp-1", "1.0.0", "prod", "deploy"), persistence, new ArrayList<>());
        run(command(null, "activity-1", null, null, null, "deploy"), persistence, new ArrayList<>());

        assertThat(taskOf(activity, "deploy").getResults()).hasSize(2);
        assertThatThrownBy(() -> run(command(null, null, "dp-1", "1.0.0", "prod", null), persistence, new ArrayList<>()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Task name is required");
        assertThatThrownBy(() -> run(command(null, null, "dp-1", null, "prod", "deploy"), persistence, new ArrayList<>()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Data product version tag is required");
    }

    private static void run(RecordTaskResultsCommand command, FakePersistence persistence, List<Activity> presented) {
        new RecordTaskResults(command, presented::add, persistence, new DirectTransactions()).execute();
    }

    private static RecordTaskResultsCommand command(String providerRunId, String activityUuid, String fqn,
                                                    String versionTag, String activityName, String taskName) {
        TaskResult result = new TaskResult();
        result.setContent("result");
        return new RecordTaskResultsCommand(providerRunId, activityUuid, fqn, versionTag, activityName, taskName,
                new ArrayList<>(List.of(result)));
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
    private static final class FakePersistence implements RecordTaskResultsPersistenceOutboundPort {

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
