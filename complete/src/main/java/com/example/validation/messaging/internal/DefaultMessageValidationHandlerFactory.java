package com.example.validation.messaging.internal;

import com.example.validation.core.api.ExternalPayloadValidator;
import com.example.validation.messaging.api.ValidatingPayloadMessageHandler;
import com.example.validation.messaging.spi.MessageValidationHandlerFactory;
import org.springframework.messaging.MessageHandler;

public class DefaultMessageValidationHandlerFactory implements MessageValidationHandlerFactory {

    private final ExternalPayloadValidator externalPayloadValidator;

    public DefaultMessageValidationHandlerFactory(ExternalPayloadValidator externalPayloadValidator) {
        this.externalPayloadValidator = externalPayloadValidator;
    }

    @Override
    public MessageHandler create(MessageHandler delegate) {
        return new ValidatingPayloadMessageHandler(delegate, externalPayloadValidator);
    }
}
