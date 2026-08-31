package com.hmg.ipmap.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = AllowedKeyValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedKeys {

    String message() default "Map contains unregistered keys";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** List of allowed keys */
    String[] value();
}
