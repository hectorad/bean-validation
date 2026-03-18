package io.github.hectorad.validation;

public record ViolationDetail(
    String propertyPath,
    String message,
    String messageTemplate,
    Object invalidValue,
    String constraintType
) {
}
