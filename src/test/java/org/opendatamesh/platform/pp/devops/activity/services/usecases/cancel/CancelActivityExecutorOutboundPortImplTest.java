package org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClient;
import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClientFactory;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskLogsRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskRunCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartResultRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStatusRes;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientException;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskMapperImpl;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cancel-run outcome of the executor port: which executor answers mean the run has already ended,
 * and which mean the cancel could not be delivered.
 */
class CancelActivityExecutorOutboundPortImplTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Feature: Cancel run through the executor
     *
     * Scenario: An executor that keeps refusing the cancel is reported as unreachable
     *   Given a running task whose executor answers 400 to every cancel
     *   When DevOps cancels the run
     *   Then it calls the executor cancel 3 times with the run command
     *   And the result is UNREACHABLE
     */
    @Test
    void whenExecutorAnswers400ToEveryAttemptThenUnreachableAfterThreeCalls() throws Exception {
        FakeExecutorClient client = new FakeExecutorClient(400);

        CancelRunResult result = port(client).cancelRun(runningTask());

        assertThat(result).isEqualTo(CancelRunResult.UNREACHABLE);
        assertThat(client.cancelCalls()).hasSize(3);
    }

    /**
     * Feature: Cancel run through the executor
     *
     * Scenario: A 404 from the executor means the run is already finished
     *   Given a running task whose executor answers 404 to the cancel
     *   When DevOps cancels the run
     *   Then the executor is called once
     *   And the result is ALREADY_FINISHED
     */
    @Test
    void whenExecutorAnswers404ThenAlreadyFinished() throws Exception {
        FakeExecutorClient client = new FakeExecutorClient(404);

        CancelRunResult result = port(client).cancelRun(runningTask());

        assertThat(result).isEqualTo(CancelRunResult.ALREADY_FINISHED);
        assertThat(client.cancelCalls()).hasSize(1);
    }

    /**
     * Feature: Cancel run through the executor
     *
     * Scenario: A 409 from the executor means the run is already finished
     *   Given a running task whose executor answers 409 to the cancel
     *   When DevOps cancels the run
     *   Then the executor is called once
     *   And the result is ALREADY_FINISHED
     */
    @Test
    void whenExecutorAnswers409ThenAlreadyFinished() throws Exception {
        FakeExecutorClient client = new FakeExecutorClient(409);

        CancelRunResult result = port(client).cancelRun(runningTask());

        assertThat(result).isEqualTo(CancelRunResult.ALREADY_FINISHED);
        assertThat(client.cancelCalls()).hasSize(1);
    }

    /**
     * Feature: Cancel run through the executor
     *
     * Scenario: A successful cancel is posted
     *   Given a running task whose executor accepts the cancel
     *   When DevOps cancels the run
     *   Then the executor is called once with the run command carrying the provider run id
     *   And the result is POSTED
     */
    @Test
    void whenExecutorAcceptsCancelThenPosted() throws Exception {
        FakeExecutorClient client = new FakeExecutorClient(null);

        CancelRunResult result = port(client).cancelRun(runningTask());

        assertThat(result).isEqualTo(CancelRunResult.POSTED);
        assertThat(client.cancelCalls()).hasSize(1);
        ExecutorTaskRunCommandRes command = client.cancelCalls().get(0);
        assertThat(command.getProviderRunId()).isEqualTo("42");
        assertThat(command.getExecutorParameters()).isNotNull();
    }

    private static CancelActivityExecutorOutboundPortImpl port(FakeExecutorClient client) {
        ExecutorClientFactory factory = (executorName, activityUuid) -> client;
        return new CancelActivityExecutorOutboundPortImpl(factory, new ExecutorServicesProperties(), new TaskMapperImpl());
    }

    private static Task runningTask() throws Exception {
        Activity activity = new Activity();
        activity.setUuid("activity-uuid");
        Task task = new Task();
        task.setUuid("task-uuid");
        task.setActivity(activity);
        task.setExecutorName("gitlab");
        task.setExecutorParameters(OBJECT_MAPPER.readTree("{}"));
        task.setProviderRunId("42");
        return task;
    }

    /**
     * Hand-written executor client: every cancel fails with the given status, or succeeds when the status is null.
     */
    private static final class FakeExecutorClient implements ExecutorClient {

        private final Integer cancelStatus;
        private final List<ExecutorTaskRunCommandRes> cancelCalls = new ArrayList<>();

        FakeExecutorClient(Integer cancelStatus) {
            this.cancelStatus = cancelStatus;
        }

        List<ExecutorTaskRunCommandRes> cancelCalls() {
            return cancelCalls;
        }

        @Override
        public void cancelTask(ExecutorTaskRunCommandRes command) {
            cancelCalls.add(command);
            if (cancelStatus != null) {
                throw new ClientException(cancelStatus, "refused");
            }
        }

        @Override
        public ExecutorTaskStartResultRes startTask(ExecutorTaskStartCommandRes command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExecutorTaskStatusRes getTaskStatus(ExecutorTaskRunCommandRes command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExecutorTaskLogsRes getTaskLogs(ExecutorTaskRunCommandRes command) {
            throw new UnsupportedOperationException();
        }
    }
}
