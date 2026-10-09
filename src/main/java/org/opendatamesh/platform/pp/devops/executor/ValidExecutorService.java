package org.opendatamesh.platform.pp.devops.executor;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = ValidExecutorServiceValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidExecutorService {

    String message() default "address is required when execution-mode is full-control";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
