package com.example.validation.core.api;

public record ViolationDetail(
    String propertyPath,
    String message,
    String messageTemplate,
    Object invalidValue,
    String constraintType
) {
}
