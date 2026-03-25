package com.example.validation.kafka.internal;

import com.example.validation.kafka.spi.KafkaValidationFailureHandler;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RecordInterceptor;

import com.example.validation.core.api.ExternalPayloadValidator;
import com.example.validation.core.api.ValidationResult;

public class ValidatingKafkaRecordInterceptor implements RecordInterceptor<Object, Object> {

    private final ExternalPayloadValidator externalPayloadValidator;

    private final KafkaValidationFailureHandler failureHandler;

    public ValidatingKafkaRecordInterceptor(
        ExternalPayloadValidator externalPayloadValidator,
        KafkaValidationFailureHandler failureHandler
    ) {
        this.externalPayloadValidator = externalPayloadValidator;
        this.failureHandler = failureHandler;
    }

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        if (record == null) {
            return null;
        }

        ValidationResult<Object> validationResult = validateRecord(record);
        if (validationResult.valid()) {
            return record;
        }

        failureHandler.handle(record, validationResult);
        return null;
    }

    @SuppressWarnings("unchecked")
    private ValidationResult<Object> validateRecord(ConsumerRecord<Object, Object> record) {
        return (ValidationResult<Object>) externalPayloadValidator.validate(record.value());
    }
}
