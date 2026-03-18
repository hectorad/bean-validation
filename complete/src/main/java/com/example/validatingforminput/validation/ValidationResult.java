package com.example.validatingforminput.validation;

import java.util.List;

public record ValidationResult<T>(T value, boolean valid, List<ViolationDetail> violations) {

    public ValidationResult {
        violations = (violations == null) ? List.of() : List.copyOf(violations);
        valid = violations.isEmpty();
    }

    public static <T> ValidationResult<T> success(T value) {
        return new ValidationResult<>(value, true, List.of());
    }

    public static <T> ValidationResult<T> failure(T value, List<ViolationDetail> violations) {
        return new ValidationResult<>(value, false, violations);
    }
}
