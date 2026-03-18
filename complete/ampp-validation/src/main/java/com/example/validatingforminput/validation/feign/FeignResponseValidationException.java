package com.example.validatingforminput.validation.feign;

import java.lang.reflect.Type;

import com.example.validatingforminput.validation.ValidationResult;

import feign.Response;
import feign.codec.DecodeException;

public class FeignResponseValidationException extends DecodeException {

    private final Object decodedPayload;

    private final ValidationResult<?> validationResult;

    private final Type targetType;

    public FeignResponseValidationException(
        Response response,
        Type targetType,
        ValidationResult<?> validationResult
    ) {
        super(response.status(), message(targetType, validationResult), response.request());
        this.decodedPayload = validationResult.value();
        this.validationResult = validationResult;
        this.targetType = targetType;
    }

    public Object getDecodedPayload() {
        return decodedPayload;
    }

    public ValidationResult<?> getValidationResult() {
        return validationResult;
    }

    public Type getTargetType() {
        return targetType;
    }

    private static String message(Type targetType, ValidationResult<?> validationResult) {
        return "Feign response validation failed for type " + targetType.getTypeName()
            + " with " + validationResult.violations().size() + " violation(s)";
    }
}
