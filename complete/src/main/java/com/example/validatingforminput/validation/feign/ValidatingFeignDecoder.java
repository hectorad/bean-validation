package com.example.validatingforminput.validation.feign;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.List;

import com.example.validatingforminput.validation.ValidationResult;
import com.example.validatingforminput.validation.ViolationDetail;

import feign.Response;
import feign.codec.Decoder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

public class ValidatingFeignDecoder implements Decoder {

    private static final Comparator<ViolationDetail> VIOLATION_ORDER = Comparator
        .comparing(ViolationDetail::propertyPath, Comparator.nullsFirst(String::compareTo))
        .thenComparing(ViolationDetail::message, Comparator.nullsFirst(String::compareTo))
        .thenComparing(ViolationDetail::constraintType, Comparator.nullsFirst(String::compareTo));

    private final Decoder delegate;

    private final Validator validator;

    public ValidatingFeignDecoder(Decoder delegate, Validator validator) {
        this.delegate = delegate;
        this.validator = validator;
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

    private ValidationResult<Object> validateDecoded(Object decoded) {
        List<ViolationDetail> violations = validator.validate(decoded).stream()
            .map(this::toViolationDetail)
            .sorted(VIOLATION_ORDER)
            .toList();

        return violations.isEmpty()
            ? ValidationResult.success(decoded)
            : ValidationResult.failure(decoded, violations);
    }

    private ViolationDetail toViolationDetail(ConstraintViolation<?> violation) {
        String constraintType = violation.getConstraintDescriptor().getAnnotation().annotationType().getName();
        return new ViolationDetail(
            violation.getPropertyPath().toString(),
            violation.getMessage(),
            violation.getMessageTemplate(),
            violation.getInvalidValue(),
            constraintType);
    }
}
