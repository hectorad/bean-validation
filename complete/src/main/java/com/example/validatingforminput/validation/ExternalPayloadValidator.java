package com.example.validatingforminput.validation;

public interface ExternalPayloadValidator {

    <T> ValidationResult<T> validate(T value);
}
