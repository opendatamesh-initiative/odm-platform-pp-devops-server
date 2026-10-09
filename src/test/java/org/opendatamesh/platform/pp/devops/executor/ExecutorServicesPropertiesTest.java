package org.opendatamesh.platform.pp.devops.executor;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executor declaration address rule.
 * Scenarios trace to {@code spdd/prompt/BDMD-5442-202610081454-[Feat]-service-instrumented-path.md}.
 */
class ExecutorServicesPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

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
        ExecutorServicesProperties.ExecutorServiceProperties declaration = new ExecutorServicesProperties.ExecutorServiceProperties();
        declaration.setExecutionMode(ExecutionMode.INSTRUMENTED);

        Set<ConstraintViolation<ExecutorServicesProperties.ExecutorServiceProperties>> violations = validator.validate(declaration);

        assertThat(violations).isEmpty();
    }

    /**
     * Feature: Executor address
     *
     * Scenario: A full-control executor still requires an address
     *   Given a declaration whose execution-mode is full-control and whose address is blank
     *   When the declaration is validated
     *   Then it is invalid
     *   And the message is "address is required when execution-mode is full-control"
     */
    @Test
    void whenFullControlAddressMissingThenInvalid() {
        ExecutorServicesProperties.ExecutorServiceProperties declaration = new ExecutorServicesProperties.ExecutorServiceProperties();
        declaration.setExecutionMode(ExecutionMode.FULL_CONTROL);
        declaration.setAddress(" ");

        Set<ConstraintViolation<ExecutorServicesProperties.ExecutorServiceProperties>> violations = validator.validate(declaration);

        assertThat(violations).isNotEmpty();
        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .contains("address is required when execution-mode is full-control");
    }
}
