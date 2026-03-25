package com.example.validation.autoconfigure;

import com.example.validation.core.api.ExternalPayloadValidator;
import com.example.validation.kafka.spi.KafkaValidationFailureHandler;
import com.example.validation.kafka.internal.LoggingKafkaValidationFailureHandler;
import com.example.validation.kafka.internal.ValidatingKafkaRecordInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.listener.RecordInterceptor;

@AutoConfiguration(after = ValidationAutoConfiguration.class)
@ConditionalOnClass(RecordInterceptor.class)
@ConditionalOnBean(ExternalPayloadValidator.class)
@ConditionalOnProperty(prefix = "com.ampp.kafka-consumer-validation", name = "enabled", havingValue = "true")
public class KafkaValidationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KafkaValidationFailureHandler kafkaValidationFailureHandler() {
        return new LoggingKafkaValidationFailureHandler();
    }

    @Bean
    @ConditionalOnMissingBean(RecordInterceptor.class)
    public RecordInterceptor<Object, Object> kafkaValidationRecordInterceptor(
        ExternalPayloadValidator externalPayloadValidator,
        KafkaValidationFailureHandler failureHandler
    ) {
        return new ValidatingKafkaRecordInterceptor(externalPayloadValidator, failureHandler);
    }
}
