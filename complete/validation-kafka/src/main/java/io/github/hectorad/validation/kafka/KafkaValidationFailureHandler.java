package io.github.hectorad.validation.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import io.github.hectorad.validation.ValidationResult;

public interface KafkaValidationFailureHandler {

    void handle(ConsumerRecord<?, ?> record, ValidationResult<?> validationResult);
}
