package com.hmg.ipmap.ingestion.file.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = JobIdValidator.class)
@Documented
public @interface JobId {
    String message() default "Job ID must be in yyyy-MM-dd format";

    boolean rejectPastDates() default false;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
