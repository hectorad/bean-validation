package com.example.validation.core.internal;

import com.example.validation.core.api.ExternalPayloadValidator;
import com.example.validation.core.api.ValidationResult;

public class NoopExternalPayloadValidator implements ExternalPayloadValidator {

    @Override
    public <T> ValidationResult<T> validate(T value) {
        return ValidationResult.success(value);
    }
}
