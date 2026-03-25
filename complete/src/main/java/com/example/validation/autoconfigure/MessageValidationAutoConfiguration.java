package com.example.validation.autoconfigure;

import com.example.validation.core.api.ExternalPayloadValidator;
import com.example.validation.messaging.internal.DefaultMessageValidationHandlerFactory;
import com.example.validation.messaging.spi.MessageValidationHandlerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.MessageHandler;

@AutoConfiguration(after = ValidationAutoConfiguration.class)
@ConditionalOnClass(MessageHandler.class)
@ConditionalOnBean(ExternalPayloadValidator.class)
@ConditionalOnProperty(prefix = "com.ampp.message-validation", name = "enabled", havingValue = "true")
public class MessageValidationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MessageValidationHandlerFactory.class)
    public MessageValidationHandlerFactory messageValidationHandlerFactory(
        ExternalPayloadValidator externalPayloadValidator
    ) {
        return new DefaultMessageValidationHandlerFactory(externalPayloadValidator);
    }
}
