package org.opendatamesh.platform.pp.devops.activity.services.usecases.executetask;

import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientException;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientTransportException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/** The sentence written in the task logs when an executor call fails. */
class ExecuteTaskExecutorOutboundPortImplTest {

    private final ExecuteTaskExecutorOutboundPortImpl port = new ExecuteTaskExecutorOutboundPortImpl(null, null, null);

    @Test
    void whenExecutorAnswersAnErrorThenStatusAndBodyAreShown() {
        String reason = port.describeFailure(task(), "start the task", new ClientException(502, "agent unreachable"));

        assertThat(reason).isEqualTo("Executor blindata failed to start the task (HTTP 502): agent unreachable");
    }

    @Test
    void whenConnectionRefusedThenCouldNotReach() {
        ClientTransportException failure = new ClientTransportException("I/O error: Connection refused",
                new ResourceAccessException("I/O error", new ConnectException("Connection refused")));

        String reason = port.describeFailure(task(), "start the task", failure);

        assertThat(reason).startsWith("Could not reach executor blindata to start the task (I/O error: Connection refused)");
        assertThat(reason).doesNotContain("HTTP 500");
    }

    @Test
    void whenReadTimesOutThenRunMayHaveStarted() {
        ClientTransportException failure = new ClientTransportException("I/O error: Read timed out",
                new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out")));

        String reason = port.describeFailure(task(), "start the task", failure);

        assertThat(reason).startsWith("Executor blindata did not answer in time when asked to start the task");
        assertThat(reason).contains("may still be acting on it");
        assertThat(reason).doesNotContain("HTTP 500");
    }

    private static Task task() {
        Task task = new Task();
        task.setExecutorName("blindata");
        return task;
    }
}
