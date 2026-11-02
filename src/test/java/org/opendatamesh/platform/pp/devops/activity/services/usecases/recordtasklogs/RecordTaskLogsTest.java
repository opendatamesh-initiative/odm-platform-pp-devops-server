package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtasklogs;

import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskLog;
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
 * Record Task Logs with hand-written ports: how the task is addressed.
 * Traces to BDMD-5443 and {@code spdd/prompt/BDMD-5443-202610291208-[Feat]-api-task-address-by-provider-run-id.md}.
 */
class RecordTaskLogsTest {

    /**
     * Feature: Address a running task by provider run id
     *
     * Scenario: Instrumented task closed by its run id
     *   Given an instrumented task is RUNNING with provider run id "gh-123"
     *   When the CLI records logs, results and status SUCCEEDED with only the provider run id
     *   Then each response is 200
     */
    @Test
    void whenOnlyRunIdGivenThenRecordsLogsOnTheRunningTask() {
        Activity activity = activity("activity-1", "dp-1", running("deploy", "gh-123"));
        FakePersistence persistence = new FakePersistence(activity);
        List<Activity> presented = new ArrayList<>();

        run(command("gh-123", null, null, null, null), persistence, presented);

        assertThat(taskOf(activity, "deploy").getLogs()).extracting(TaskLog::getContent).containsExactly("log");
        assertThat(presented).containsExactly(activity);
    }

    /**
     * Feature: Address a running task by provider run id
     *
     * Scenario: Logs by run id refused for a full-control task
     *   Given a full-control task is RUNNING with provider run id "gl-42"
     *   When the CLI records logs with that provider run id
     *   Then the response is 400 "Task <name> is not instrumented"
     */
    @Test
    void whenRunIdMatchesFullControlTaskThenNotInstrumented() {
        Activity activity = activity("activity-1", "dp-1", task("deploy", ExecutionStatus.RUNNING, "gl-42", "gitlab"));
        FakePersistence persistence = new FakePersistence(activity);

        assertThatThrownBy(() -> run(command("gl-42", null, null, null, null), persistence, new ArrayList<>()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Task deploy is not instrumented");
        assertThat(persistence.saved).isEmpty();
    }

    /**
     * Feature: Address a running task by provider run id
     *
     * Scenario: Run id shared by two running tasks is refused
     *   Given two activities each have a RUNNING task with provider run id "gh-123"
     *   When the CLI records the status with that provider run id
     *   Then the response is 400 naming both tasks and activities
     */
    @Test
    void whenRunIdSharedByTwoRunningTasksThenBadRequest() {
        FakePersistence persistence = new FakePersistence(
                activity("activity-1", "dp-1", running("build", "gh-123")),
                activity("activity-2", "dp-2", running("deploy", "gh-123")));

        assertThatThrownBy(() -> run(command("gh-123", null, null, null, null), persistence, new ArrayList<>()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Provider run id gh-123 matches more than one RUNNING task: "
                        + "task build of activity activity-1, task deploy of activity activity-2");
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
        FakePersistence persistence = new FakePersistence(
                activity("activity-1", "dp-1", task("deploy", ExecutionStatus.SUCCEEDED, "gh-123", "cli")));

        assertThatThrownBy(() -> run(command("gh-123", null, null, null, null), persistence, new ArrayList<>()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No RUNNING task with provider run id gh-123");
    }

    /**
     * Feature: Address a running task by provider run id
     *
     * Scenario: Run id of a finished task is not matched
     *   Given a task of an earlier activity is SUCCEEDED with provider run id "gh-123"
     *   And a task of a new activity is RUNNING with provider run id "gh-123"
     *   When the CLI records logs with that provider run id
     *   Then the log is stored on the RUNNING task only
     *
     * Same activity: "build" is SUCCEEDED and "deploy" is RUNNING, both with run id "gh-123".
     * The fake returns the activity, as the real query does, so only the use case's per-task RUNNING filter separates them.
     */
    @Test
    void whenSameActivityHasFinishedAndRunningTaskWithRunIdThenOnlyRunningTaskLogged() {
        Activity activity = activity("activity-1", "dp-1",
                task("build", ExecutionStatus.SUCCEEDED, "gh-123", "cli"), running("deploy", "gh-123"));
        FakePersistence persistence = new FakePersistence(activity);

        run(command("gh-123", null, null, null, null), persistence, new ArrayList<>());

        assertThat(taskOf(activity, "deploy").getLogs()).extracting(TaskLog::getContent).containsExactly("log");
        assertThat(taskOf(activity, "build").getLogs()).isNullOrEmpty();
    }

    /**
     * Extra test, not a mapped scenario: for logs, a provider run id together with a natural key is refused,
     * and so is a request with no address.
     */
    @Test
    void whenRunIdAndNaturalKeyThenBadRequestAndNoAddressThenBadRequest() {
        FakePersistence persistence = new FakePersistence();

        assertThatThrownBy(() -> run(command("gh-123", "dp-1", "1.0.0", "prod", "deploy"), persistence, new ArrayList<>()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Send only one task address: provider run id, or data product FQN, version tag, and activity name");
        assertThatThrownBy(() -> run(command(null, null, null, null, "deploy"), persistence, new ArrayList<>()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Provider run id or data product FQN, version tag, and activity name are required");
    }

    private static void run(RecordTaskLogsCommand command, FakePersistence persistence, List<Activity> presented) {
        new RecordTaskLogs(command, presented::add, persistence,
                executorName -> "cli".equals(executorName) ? ExecutionMode.INSTRUMENTED : ExecutionMode.FULL_CONTROL,
                new DirectTransactions()).execute();
    }

    private static RecordTaskLogsCommand command(String providerRunId, String fqn, String versionTag,
                                                 String activityName, String taskName) {
        TaskLog log = new TaskLog();
        log.setContent("log");
        return new RecordTaskLogsCommand(providerRunId, fqn, versionTag, activityName, taskName,
                new ArrayList<>(List.of(log)));
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
    private static final class FakePersistence implements RecordTaskLogsPersistenceOutboundPort {

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
