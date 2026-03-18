package com.example.validatingforminput.validation.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.validatingforminput.validation.ValidationResult;

public class LoggingKafkaValidationFailureHandler implements KafkaValidationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingKafkaValidationFailureHandler.class);

    @Override
    public void handle(ConsumerRecord<?, ?> record, ValidationResult<?> validationResult) {
        log.warn(
            "Skipping Kafka record due to validation failure. topic={}, partition={}, offset={}, key={}, violations={}",
            record.topic(),
            record.partition(),
            record.offset(),
            record.key(),
            validationResult.violations());
    }
}
