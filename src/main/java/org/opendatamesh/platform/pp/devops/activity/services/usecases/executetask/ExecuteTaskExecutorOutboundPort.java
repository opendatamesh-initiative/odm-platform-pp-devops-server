package org.opendatamesh.platform.pp.devops.activity.services.usecases.executetask;

import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskLog;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
import org.opendatamesh.platform.pp.devops.executor.ExecutorRunStatus;

import java.util.Map;
import java.util.Optional;

interface ExecuteTaskExecutorOutboundPort {
    ExecutionMode findExecutionMode(String executorName);

    String startRun(Task task, Map<String, String> resolvedPipelineParameters);

    StatusRead readRunStatus(Task task);

    void waitBeforeNextStatusRead();

    int maxStatusReads();

    Optional<TaskLog> readRunLog(Task task);

    /**
     * A sentence for the user explaining why an executor call failed: the executor's error body when it answered,
     * the transport error otherwise.
     */
    String describeFailure(Task task, ExecutorCall call, RuntimeException failure);

    /** The executor calls whose failure is explained to the user. Only a start can leave a run DevOps does not track. */
    enum ExecutorCall {
        START("start the task"),
        READ_STATUS("read the run status"),
        READ_LOGS("read the run logs");

        private final String action;

        ExecutorCall(String action) {
            this.action = action;
        }

        String action() {
            return action;
        }
    }

    /**
     * One status read. {@code failureReason} is set when the executor's answer itself is unusable (no status, an
     * unknown status): the run is then FAILED and the reason goes to the task logs.
     */
    record StatusRead(ExecutorRunStatus status, String failureReason) {

        static StatusRead of(ExecutorRunStatus status) {
            return new StatusRead(status, null);
        }

        static StatusRead unusable(String failureReason) {
            return new StatusRead(ExecutorRunStatus.FAILED, failureReason);
        }
    }
}
