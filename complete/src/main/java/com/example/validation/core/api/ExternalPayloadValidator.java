package com.example.validation.core.api;

public interface ExternalPayloadValidator {

    <T> ValidationResult<T> validate(T value);
}
