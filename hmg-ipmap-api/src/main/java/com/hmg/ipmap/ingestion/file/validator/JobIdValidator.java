package com.hmg.ipmap.ingestion.file.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class JobIdValidator implements ConstraintValidator<JobId, String> {

    private static final String JOB_ID_PATTERN = "\\d{4}-\\d{2}-\\d{2}";

    private boolean rejectPastDates;

    @Override
    public void initialize(JobId annotation) {
        this.rejectPastDates = annotation.rejectPastDates();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || !value.matches(JOB_ID_PATTERN)) {
            return false;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(value);
        } catch (DateTimeParseException _) {
            return false;
        }
        return !rejectPastDates || !date.isBefore(LocalDate.now());
    }
}
