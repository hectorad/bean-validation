package com.example.validation.core.internal;

import com.example.validation.core.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.listener.RecordInterceptor;

import com.example.validation.autoconfigure.KafkaValidationAutoConfiguration;
import com.example.validation.autoconfigure.ValidationAutoConfiguration;
import com.example.validation.kafka.spi.KafkaValidationFailureHandler;
import com.example.validation.kafka.internal.ValidatingKafkaRecordInterceptor;

class KafkaValidationAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            ValidationAutoConfiguration.class,
            KafkaValidationAutoConfiguration.class));

    @Test
    void shouldNotCreateKafkaInterceptorWhenFeatureFlagIsDisabled() {
        contextRunner.run(context -> assertThat(context.getBeansOfType(RecordInterceptor.class)).isEmpty());
    }

    @Test
    void shouldCreateKafkaInterceptorWhenFeatureFlagIsEnabled() {
        contextRunner
            .withPropertyValues("com.ampp.kafka-consumer-validation.enabled=true")
            .run(context -> assertThat(context.getBeansOfType(RecordInterceptor.class)).hasSize(1));
    }

    @Test
    void shouldPassThroughValidRecord() {
        ExternalPayloadValidator validator = new StubExternalPayloadValidator(ValidationResult.success("value"));
        AtomicReference<ValidationResult<?>> capturedResult = new AtomicReference<>();
        KafkaValidationFailureHandler failureHandler = (record, validationResult) -> capturedResult.set(validationResult);
        ValidatingKafkaRecordInterceptor interceptor = new ValidatingKafkaRecordInterceptor(validator, failureHandler);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("people", 0, 10L, "key", "value");

        ConsumerRecord<Object, Object> intercepted = interceptor.intercept(record, null);

        assertThat(intercepted).isSameAs(record);
        assertThat(capturedResult).hasValue(null);
    }

    @Test
    void shouldHandleInvalidRecordAndSkipListener() {
        ValidationResult<Object> validationResult =
            ValidationResult.failure("value", List.of(new ViolationDetail("name", "must not be blank", "{message}", "", "NotBlank")));
        ExternalPayloadValidator validator = new StubExternalPayloadValidator(validationResult);
        AtomicReference<ConsumerRecord<?, ?>> capturedRecord = new AtomicReference<>();
        AtomicReference<ValidationResult<?>> capturedResult = new AtomicReference<>();
        KafkaValidationFailureHandler failureHandler = (record, result) -> {
            capturedRecord.set(record);
            capturedResult.set(result);
        };
        ValidatingKafkaRecordInterceptor interceptor = new ValidatingKafkaRecordInterceptor(validator, failureHandler);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("people", 1, 25L, "key", "value");

        ConsumerRecord<Object, Object> intercepted = interceptor.intercept(record, null);

        assertThat(intercepted).isNull();
        assertThat(capturedRecord).hasValue(record);
        assertThat(capturedResult).hasValue(validationResult);
    }

    @Test
    void shouldPropagateHandlerFailures() {
        ExternalPayloadValidator validator = new StubExternalPayloadValidator(
            ValidationResult.failure("value", List.of(new ViolationDetail("name", "invalid", "{message}", "value", "NotBlank"))));
        KafkaValidationFailureHandler failureHandler = (record, result) -> {
            throw new IllegalStateException("boom");
        };
        ValidatingKafkaRecordInterceptor interceptor = new ValidatingKafkaRecordInterceptor(validator, failureHandler);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("people", 0, 1L, "key", "value");

        assertThatThrownBy(() -> interceptor.intercept(record, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("boom");
    }

    private static final class StubExternalPayloadValidator implements ExternalPayloadValidator {

        private final ValidationResult<?> validationResult;

        private StubExternalPayloadValidator(ValidationResult<?> validationResult) {
            this.validationResult = validationResult;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ValidationResult<T> validate(T value) {
            return (ValidationResult<T>) validationResult;
        }
    }
}
