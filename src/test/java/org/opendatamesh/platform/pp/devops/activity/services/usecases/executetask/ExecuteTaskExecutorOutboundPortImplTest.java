package org.opendatamesh.platform.pp.devops.activity.services.usecases.executetask;

import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.executetask.ExecuteTaskExecutorOutboundPort.ExecutorCall;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientRequestFailedException;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientException;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientTransportException;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientUnreadableResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/** The sentence written in the task logs when an executor call fails. */
class ExecuteTaskExecutorOutboundPortImplTest {

    private final ExecuteTaskExecutorOutboundPortImpl port = new ExecuteTaskExecutorOutboundPortImpl(null, null, null);

    @Test
    void whenExecutorAnswersAnErrorThenStatusAndBodyAreShown() {
        String reason = port.describeFailure(task(), ExecutorCall.START, new ClientException(502, "agent unreachable"));

        assertThat(reason).isEqualTo("Executor blindata failed to start the task (HTTP 502): agent unreachable");
    }

    @Test
    void whenConnectionRefusedThenCouldNotReach() {
        ClientTransportException failure = new ClientTransportException("I/O error: Connection refused",
                new ResourceAccessException("I/O error", new ConnectException("Connection refused")));

        String reason = port.describeFailure(task(), ExecutorCall.START, failure);

        assertThat(reason).startsWith("Could not reach executor blindata to start the task (I/O error: Connection refused)");
        assertThat(reason).doesNotContain("HTTP 500");
    }

    @Test
    void whenReadTimesOutThenRunMayHaveStarted() {
        ClientTransportException failure = new ClientTransportException("I/O error: Read timed out",
                new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out")));

        String reason = port.describeFailure(task(), ExecutorCall.START, failure);

        assertThat(reason).startsWith("Executor blindata did not answer when asked to start the task");
        assertThat(reason).contains("may have started the run");
        assertThat(reason).doesNotContain("HTTP 500");
    }

    @Test
    void whenStatusReadTimesOutThenNoUntrackedRunNote() {
        ClientTransportException failure = new ClientTransportException("I/O error: Read timed out",
                new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out")));

        String reason = port.describeFailure(task(), ExecutorCall.READ_STATUS, failure);

        assertThat(reason).isEqualTo("Executor blindata did not answer when asked to read the run status (I/O error: Read timed out).");
    }

    @Test
    void whenConnectionClosedWithoutAnswerThenRequestCountsAsSent() {
        ClientTransportException failure = new ClientTransportException("I/O error: target server failed to respond",
                new ResourceAccessException("I/O error", new NoHttpResponseException("target server failed to respond")));

        String reason = port.describeFailure(task(), ExecutorCall.START, failure);

        assertThat(reason).startsWith("Executor blindata did not answer when asked to start the task");
    }

    @Test
    void whenAnswerUnreadableThenNoHttpStatusClaimed() {
        ClientUnreadableResponseException failure = new ClientUnreadableResponseException(
                "Error while extracting response for type [ExecutorTaskStartResultRes]", new RuntimeException());

        String reason = port.describeFailure(task(), ExecutorCall.START, failure);

        assertThat(reason).isEqualTo("Executor blindata returned an answer that could not be read when asked to start the "
                + "task (Error while extracting response for type [ExecutorTaskStartResultRes])");
    }

    @Test
    void whenConnectionResetThenRequestCountsAsSent() {
        ClientTransportException failure = new ClientTransportException("I/O error: Connection reset",
                new ResourceAccessException("I/O error", new SocketException("Connection reset")));

        String reason = port.describeFailure(task(), ExecutorCall.START, failure);

        assertThat(reason).startsWith("Executor blindata did not answer when asked to start the task");
        assertThat(reason).contains("may have started the run");
    }

    @Test
    void whenRequestCouldNotBeSentThenNoAnswerClaimed() {
        ClientRequestFailedException failure = new ClientRequestFailedException(
                "No HttpMessageConverter for java.lang.Object", new RuntimeException());

        String reason = port.describeFailure(task(), ExecutorCall.START, failure);

        assertThat(reason).isEqualTo("DevOps could not send the request to executor blindata to start the task: "
                + "No HttpMessageConverter for java.lang.Object");
    }

    private static Task task() {
        Task task = new Task();
        task.setExecutorName("blindata");
        return task;
    }
}
