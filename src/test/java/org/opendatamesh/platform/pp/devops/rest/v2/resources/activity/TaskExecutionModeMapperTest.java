package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity;

import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.devops.executor.ExecutionMode;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Execution mode lookup used when a task is mapped to a resource.
 * Scenarios trace to {@code spdd/prompt/BDMD-5518-202611031629-[Feat]-api-task-execution-mode.md}.
 */
class TaskExecutionModeMapperTest {

    /**
     * Feature: Execution mode on tasks
     *
     * Scenario: Mode from the executor declaration
     *   Given executor "starter" is declared full-control and "cli" instrumented
     *   When the mode of each is resolved
     *   Then "starter" is FULL_CONTROL and "cli" is INSTRUMENTED
     */
    @Test
    void whenExecutorDeclaredThenItsMode() {
        TaskExecutionModeMapper mapper = new TaskExecutionModeMapper(properties());

        assertThat(mapper.executionModeOf("starter")).isEqualTo(ExecutionMode.FULL_CONTROL);
        assertThat(mapper.executionModeOf("cli")).isEqualTo(ExecutionMode.INSTRUMENTED);
    }

    /**
     * Feature: Execution mode on tasks
     *
     * Scenario: Executor no longer declared
     *   Given an executor name that is not declared, blank, or missing
     *   When its mode is resolved
     *   Then the mode is null
     *   And nothing is thrown
     */
    @Test
    void whenExecutorNotDeclaredOrBlankThenNull() {
        TaskExecutionModeMapper mapper = new TaskExecutionModeMapper(properties());

        assertThat(mapper.executionModeOf("retired")).isNull();
        assertThat(mapper.executionModeOf(" ")).isNull();
        assertThat(mapper.executionModeOf(null)).isNull();
    }

    private static ExecutorServicesProperties properties() {
        ExecutorServicesProperties properties = new ExecutorServicesProperties();
        properties.getServices().put("starter", declaration(ExecutionMode.FULL_CONTROL));
        properties.getServices().put("cli", declaration(ExecutionMode.INSTRUMENTED));
        return properties;
    }

    private static ExecutorServicesProperties.ExecutorServiceProperties declaration(ExecutionMode mode) {
        ExecutorServicesProperties.ExecutorServiceProperties declaration = new ExecutorServicesProperties.ExecutorServiceProperties();
        declaration.setExecutionMode(mode);
        declaration.setAddress("http://localhost:9080");
        return declaration;
    }
}
