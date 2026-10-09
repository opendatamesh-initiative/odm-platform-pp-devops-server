package org.opendatamesh.platform.pp.devops.executor;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

public class ValidExecutorServiceValidator
        implements ConstraintValidator<ValidExecutorService, ExecutorServicesProperties.ExecutorServiceProperties> {

    @Override
    public boolean isValid(ExecutorServicesProperties.ExecutorServiceProperties value, ConstraintValidatorContext context) {
        if (value == null || value.getExecutionMode() == null) {
            return true;
        }
        if (value.getExecutionMode() == ExecutionMode.INSTRUMENTED) {
            return true;
        }
        return StringUtils.hasText(value.getAddress());
    }
}
