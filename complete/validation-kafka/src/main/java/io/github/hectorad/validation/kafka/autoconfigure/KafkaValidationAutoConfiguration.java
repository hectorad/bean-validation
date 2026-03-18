package io.github.hectorad.validation.kafka.autoconfigure;

import io.github.hectorad.validation.ExternalPayloadValidator;
import io.github.hectorad.validation.kafka.KafkaValidationFailureHandler;
import io.github.hectorad.validation.kafka.LoggingKafkaValidationFailureHandler;
import io.github.hectorad.validation.kafka.ValidatingKafkaRecordInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.listener.RecordInterceptor;

@AutoConfiguration(afterName = "io.github.hectorad.validation.autoconfigure.ValidationAutoConfiguration")
@ConditionalOnClass(RecordInterceptor.class)
@ConditionalOnBean(ExternalPayloadValidator.class)
@ConditionalOnProperty(prefix = "hector.validation.kafka", name = "enabled", havingValue = "true")
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
