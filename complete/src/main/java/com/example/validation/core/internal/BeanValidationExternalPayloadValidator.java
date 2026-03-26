package com.example.validation.core.internal;

import java.util.Comparator;
import java.util.List;

import com.example.validation.core.api.ExternalPayloadValidator;
import com.example.validation.core.api.ValidationResult;
import com.example.validation.core.api.ViolationDetail;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

public class BeanValidationExternalPayloadValidator implements ExternalPayloadValidator {

    private static final Comparator<ViolationDetail> VIOLATION_ORDER = Comparator
        .comparing(ViolationDetail::propertyPath, Comparator.nullsFirst(String::compareTo))
        .thenComparing(ViolationDetail::message, Comparator.nullsFirst(String::compareTo))
        .thenComparing(ViolationDetail::constraintType, Comparator.nullsFirst(String::compareTo));

    private final Validator validator;

    public BeanValidationExternalPayloadValidator(Validator validator) {
        this.validator = validator;
    }

    @Override
    public <T> ValidationResult<T> validate(T value) {
        if (value == null) {
            return ValidationResult.success(null);
        }

        var violationSet = validator.validate(value);
        if (violationSet.isEmpty()) {
            return ValidationResult.success(value);
        }

        List<ViolationDetail> violations = violationSet.stream()
            .map(this::toViolationDetail)
            .sorted(VIOLATION_ORDER)
            .toList();

        return ValidationResult.failure(value, violations);
    }

    private ViolationDetail toViolationDetail(ConstraintViolation<?> violation) {
        String constraintType = violation.getConstraintDescriptor().getAnnotation().annotationType().getName();
        return new ViolationDetail(
            violation.getPropertyPath().toString(),
            violation.getMessage(),
            violation.getMessageTemplate(),
            violation.getInvalidValue(),
            constraintType);
    }
}
