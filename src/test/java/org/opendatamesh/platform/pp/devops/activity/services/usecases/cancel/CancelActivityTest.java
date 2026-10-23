package org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cancel Activity with hand-written ports: what the use case hands to the executor port for a running task.
 */
class CancelActivityTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String ACTIVITY_UUID = "activity-uuid";
    private static final String TASK_UUID = "task-uuid";

    /**
     * Feature: Cancel an activity
     *
     * Scenario: The cancel of a RUNNING full-control task carries the task's stored executor parameters
     *   Given a full-control task is RUNNING with a provider run id and stored executor parameters
     *   When the user cancels the activity
     *   Then the executor port receives that task with its executor name, provider run id and executor parameters
     *   And the cancel completes with the CANCELED activity
     */
    @Test
    void whenRunningFullControlTaskIsCanceledThenExecutorReceivesItsExecutorParameters() throws Exception {
        JsonNode executorParameters = OBJECT_MAPPER.readTree(
                "{\"dataProductRepo\":{\"providerBaseUrl\":\"https://gitlab.com\",\"externalIdentifier\":\"87917409\"},"
                        + "\"ref\":{\"name\":\"main\",\"type\":\"BRANCH\"}}");
        FakePersistence persistence = new FakePersistence(executorParameters);
        FakeExecutor executor = new FakeExecutor(persistence);
        List<Activity> presented = new ArrayList<>();

        new CancelActivity(
                new CancelActivityCommand(ACTIVITY_UUID),
                presented::add,
                persistence,
                activityUuid -> { },
                executor,
                new NoWaitPolling(),
                new DirectTransactions()
        ).execute();

        assertThat(executor.canceled).singleElement().satisfies(task -> {
            assertThat(task.getUuid()).isEqualTo(TASK_UUID);
            assertThat(task.getExecutorName()).isEqualTo("gitlab");
            assertThat(task.getProviderRunId()).isEqualTo("42");
            assertThat(task.getExecutorParameters()).isEqualTo(executorParameters);
            assertThat(task.getActivity().getUuid()).isEqualTo(ACTIVITY_UUID);
        });
        assertThat(presented).singleElement()
                .satisfies(activity -> assertThat(activity.getStatus()).isEqualTo(ExecutionStatus.CANCELED));
    }

    /**
     * Stored activity with one task. Every read returns a fresh copy, as a detached read from the database would.
     */
    private static final class FakePersistence implements CancelActivityPersistenceOutboundPort {

        private final JsonNode executorParameters;
        private ExecutionStatus activityStatus = ExecutionStatus.RUNNING;
        private ExecutionStatus taskStatus = ExecutionStatus.RUNNING;

        FakePersistence(JsonNode executorParameters) {
            this.executorParameters = executorParameters;
        }

        void runCanceled() {
            activityStatus = ExecutionStatus.CANCELED;
            taskStatus = ExecutionStatus.CANCELED;
        }

        @Override
        public Activity findForUpdate(String uuid) {
            return stored();
        }

        @Override
        public Activity save(Activity activity) {
            return activity;
        }

        @Override
        public Activity findDetached(String uuid) {
            return stored();
        }

        private Activity stored() {
            Activity activity = new Activity();
            activity.setUuid(ACTIVITY_UUID);
            activity.setStatus(activityStatus);
            Task task = new Task();
            task.setUuid(TASK_UUID);
            task.setActivity(activity);
            task.setSortOrder(0);
            task.setStatus(taskStatus);
            task.setExecutorName("gitlab");
            task.setExecutorParameters(executorParameters.deepCopy());
            task.setProviderRunId("42");
            activity.setTasks(new ArrayList<>(List.of(task)));
            return activity;
        }
    }

    /**
     * Full-control executor that accepts the cancel and ends the run at once.
     */
    private static final class FakeExecutor implements CancelActivityExecutorOutboundPort {

        private final FakePersistence persistence;
        private final List<Task> canceled = new ArrayList<>();

        FakeExecutor(FakePersistence persistence) {
            this.persistence = persistence;
        }

        @Override
        public ExecutionMode findExecutionMode(String executorName) {
            return ExecutionMode.FULL_CONTROL;
        }

        @Override
        public CancelRunResult cancelRun(Task task) {
            canceled.add(task);
            persistence.runCanceled();
            return CancelRunResult.POSTED;
        }
    }

    private static final class NoWaitPolling implements CancelActivityPollingOutboundPort {

        @Override
        public int maxStatusReads() {
            return 3;
        }

        @Override
        public void waitOnePollInterval() {
        }

        @Override
        public void waitOneSecond() {
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
