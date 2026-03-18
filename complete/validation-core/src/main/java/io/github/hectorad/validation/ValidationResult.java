package io.github.hectorad.validation;

import java.util.List;

public record ValidationResult<T>(T value, List<ViolationDetail> violations) {

    public ValidationResult {
        violations = (violations == null) ? List.of() : List.copyOf(violations);
    }

    public boolean valid() {
        return violations.isEmpty();
    }

    public static <T> ValidationResult<T> success(T value) {
        return new ValidationResult<>(value, List.of());
    }

    public static <T> ValidationResult<T> failure(T value, List<ViolationDetail> violations) {
        return new ValidationResult<>(value, violations);
    }
}
