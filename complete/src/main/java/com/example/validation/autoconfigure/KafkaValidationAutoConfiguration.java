package com.example.validation.autoconfigure;

import com.example.validatingforminput.validation.ExternalPayloadValidator;
import com.example.validatingforminput.validation.kafka.KafkaValidationFailureHandler;
import com.example.validatingforminput.validation.kafka.LoggingKafkaValidationFailureHandler;
import com.example.validatingforminput.validation.kafka.ValidatingKafkaRecordInterceptor;
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
