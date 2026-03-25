package com.example.validation.kafka.spi;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.example.validation.core.api.ValidationResult;

public interface KafkaValidationFailureHandler {

    void handle(ConsumerRecord<?, ?> record, ValidationResult<?> validationResult);
}
