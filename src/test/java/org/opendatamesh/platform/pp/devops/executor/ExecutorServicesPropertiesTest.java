package org.opendatamesh.platform.pp.devops.executor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Executor declaration address rule.
 * Scenarios trace to {@code spdd/prompt/BDMD-5442-202610081454-[Feat]-service-instrumented-path.md}.
 */
class ExecutorServicesPropertiesTest {

    /**
     * Feature: Executor address
     *
     * Scenario: An instrumented executor may omit its address
     *   Given a declaration whose execution-mode is instrumented and whose address is absent
     *   When the declaration is validated
     *   Then it is valid
     */
    @Test
    void whenInstrumentedAddressMissingThenValid() {
        ExecutorServicesProperties properties = new ExecutorServicesProperties();
        ExecutorServicesProperties.ExecutorServiceProperties declaration = new ExecutorServicesProperties.ExecutorServiceProperties();
        declaration.setExecutionMode(ExecutionMode.INSTRUMENTED);
        properties.getServices().put("cli", declaration);

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    /**
     * Feature: Executor address
     *
     * Scenario: A full-control executor still requires an address
     *   Given a declaration whose execution-mode is full-control and whose address is blank
     *   When the declaration is validated
     *   Then it is invalid
     *   And the message names the executor and says address is required when execution-mode is full-control
     */
    @Test
    void whenFullControlAddressMissingThenInvalid() {
        ExecutorServicesProperties properties = new ExecutorServicesProperties();
        ExecutorServicesProperties.ExecutorServiceProperties declaration = new ExecutorServicesProperties.ExecutorServiceProperties();
        declaration.setExecutionMode(ExecutionMode.FULL_CONTROL);
        declaration.setAddress(" ");
        properties.getServices().put("starter", declaration);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Executor starter: address is required when execution-mode is full-control");
    }
}
