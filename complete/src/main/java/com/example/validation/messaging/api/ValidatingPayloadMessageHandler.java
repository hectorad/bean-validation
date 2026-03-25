package com.example.validation.messaging.api;

import com.example.validation.core.api.ExternalPayloadValidator;
import com.example.validation.core.api.ValidationResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;

public class ValidatingPayloadMessageHandler implements MessageHandler {

    private final MessageHandler delegate;

    private final ExternalPayloadValidator externalPayloadValidator;

    public ValidatingPayloadMessageHandler(
        MessageHandler delegate,
        ExternalPayloadValidator externalPayloadValidator
    ) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate MessageHandler must not be null.");
        }
        if (externalPayloadValidator == null) {
            throw new IllegalArgumentException("ExternalPayloadValidator must not be null.");
        }
        this.delegate = delegate;
        this.externalPayloadValidator = externalPayloadValidator;
    }

    @Override
    public void handleMessage(Message<?> message) {
        if (message == null) {
            throw new IllegalArgumentException("Message must not be null.");
        }

        ValidationResult<Object> validationResult = validatePayload(message);
        if (!validationResult.valid()) {
            throw new MessagePayloadValidationException(message, validationResult);
        }
        delegate.handleMessage(message);
    }

    @SuppressWarnings("unchecked")
    private ValidationResult<Object> validatePayload(Message<?> message) {
        return (ValidationResult<Object>) externalPayloadValidator.validate(message.getPayload());
    }
}
