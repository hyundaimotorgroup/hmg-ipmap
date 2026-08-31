package com.hmg.ipmap.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Map;
import java.util.Set;

public class AllowedKeyValidator implements ConstraintValidator<AllowedKeys, Map<String, String>> {

    private Set<String> allowedKeys;

    @Override
    public void initialize(AllowedKeys constraintAnnotation) {
        allowedKeys = Set.of(constraintAnnotation.value());
    }

    @Override
    public boolean isValid(Map<String, String> value, ConstraintValidatorContext context) {

        if (value == null || value.isEmpty()) {
            return true;
        }

        for (String key : value.keySet()) {
            if (!allowedKeys.contains(key)) {
                return false;
            }
        }
        return true;
    }
}
