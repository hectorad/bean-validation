package io.github.hectorad.validation.feign;

import java.io.IOException;
import java.lang.reflect.Type;

import io.github.hectorad.validation.ExternalPayloadValidator;
import io.github.hectorad.validation.ValidationResult;

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

        ValidationResult<Object> validationResult = validateDecoded(decoded);
        if (validationResult.valid()) {
            return decoded;
        }

        throw new FeignResponseValidationException(response, type, validationResult);
    }

    @SuppressWarnings("unchecked")
    private ValidationResult<Object> validateDecoded(Object decoded) {
        return (ValidationResult<Object>) externalPayloadValidator.validate(decoded);
    }
}
