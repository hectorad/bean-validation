package com.example.validation.messaging.spi;

import org.springframework.messaging.MessageHandler;

public interface MessageValidationHandlerFactory {

    MessageHandler create(MessageHandler delegate);
}
