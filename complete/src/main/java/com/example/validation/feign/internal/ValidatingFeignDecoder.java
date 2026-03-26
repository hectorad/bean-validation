package com.example.validation.feign.internal;

import java.io.IOException;
import java.lang.reflect.Type;

import com.example.validation.core.api.ExternalPayloadValidator;
import com.example.validation.core.api.ValidationResult;
import com.example.validation.feign.api.FeignResponseValidationException;

import feign.Response;
import feign.codec.Decoder;

public class ValidatingFeignDecoder implements Decoder {

    private final Decoder delegate;

    private final ExternalPayloadValidator externalPayloadValidator;

    public ValidatingFeignDecoder(Decoder delegate, ExternalPayloadValidator externalPayloadValidator) {
        this.delegate = delegate;
        this.externalPayloadValidator = externalPayloadValidator;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException {
        Object decoded = delegate.decode(response, type);
        if (decoded == null) {
            return null;
        }

        ValidationResult<Object> validationResult = externalPayloadValidator.validate(decoded);
        if (validationResult.valid()) {
            return decoded;
        }

        throw new FeignResponseValidationException(response, type, validationResult);
    }
}
