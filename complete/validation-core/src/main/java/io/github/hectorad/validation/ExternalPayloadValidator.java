package io.github.hectorad.validation;

public interface ExternalPayloadValidator {

    <T> ValidationResult<T> validate(T value);
}
