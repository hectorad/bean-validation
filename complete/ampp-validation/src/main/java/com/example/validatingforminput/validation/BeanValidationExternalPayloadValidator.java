package com.example.validatingforminput.validation;

import java.util.Comparator;
import java.util.List;

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

        List<ViolationDetail> violations = validator.validate(value).stream()
            .map(this::toViolationDetail)
            .sorted(VIOLATION_ORDER)
            .toList();

        return violations.isEmpty()
            ? ValidationResult.success(value)
            : ValidationResult.failure(value, violations);
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
