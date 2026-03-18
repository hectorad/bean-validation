package com.example.validatingforminput.validation.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.example.validatingforminput.validation.ValidationResult;

public interface KafkaValidationFailureHandler {

    void handle(ConsumerRecord<?, ?> record, ValidationResult<?> validationResult);
}
