package com.example.validation.messaging.api;

import com.example.validation.core.api.ValidationResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;

public class MessagePayloadValidationException extends MessageHandlingException {

    private final ValidationResult<?> validationResult;

    public MessagePayloadValidationException(Message<?> failedMessage, ValidationResult<?> validationResult) {
        super(failedMessage, message(validationResult));
        this.validationResult = validationResult;
    }

    public ValidationResult<?> getValidationResult() {
        return validationResult;
    }

    private static String message(ValidationResult<?> validationResult) {
        Object payload = validationResult.value();
        String payloadType = (payload != null) ? payload.getClass().getName() : "null";
        return "Message payload validation failed for type " + payloadType
            + " with " + validationResult.violations().size() + " violation(s)";
    }
}
