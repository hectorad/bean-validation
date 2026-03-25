package com.example.validation.core.internal;

import com.example.validation.autoconfigure.MessageValidationAutoConfiguration;
import com.example.validation.autoconfigure.ValidationAutoConfiguration;
import com.example.validation.core.api.ExternalPayloadValidator;
import com.example.validation.core.api.ValidationResult;
import com.example.validation.core.api.ViolationDetail;
import com.example.validation.messaging.api.MessagePayloadValidationException;
import com.example.validation.messaging.api.ValidatingPayloadMessageHandler;
import com.example.validation.messaging.spi.MessageValidationHandlerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageValidationAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            ValidationAutoConfiguration.class,
            MessageValidationAutoConfiguration.class));

    @Test
    void shouldNotCreateMessageValidationBeansWhenFeatureFlagIsDisabled() {
        contextRunner.run(context -> {
            assertThat(context.getBeansOfType(MessageValidationHandlerFactory.class)).isEmpty();
            assertThat(context.getBeansOfType(MessageHandler.class)).isEmpty();
            assertThat(context.getBeansOfType(MessageChannel.class)).isEmpty();
        });
    }

    @Test
    void shouldCreateMessageValidationFactoryWhenFeatureFlagIsEnabled() {
        contextRunner
            .withPropertyValues("com.ampp.message-validation.enabled=true")
            .run(context -> {
                assertThat(context.getBeansOfType(MessageValidationHandlerFactory.class)).hasSize(1);
                assertThat(context.getBeansOfType(MessageHandler.class)).isEmpty();
                assertThat(context.getBeansOfType(MessageChannel.class)).isEmpty();
            });
    }

    @Test
    void shouldPassThroughValidMessageToDelegate() {
        ExternalPayloadValidator validator = new StubExternalPayloadValidator(ValidationResult.success("value"));
        AtomicReference<Message<?>> capturedMessage = new AtomicReference<>();
        MessageHandler delegate = capturedMessage::set;
        ValidatingPayloadMessageHandler handler = new ValidatingPayloadMessageHandler(delegate, validator);
        Message<String> message = MessageBuilder.withPayload("value").build();

        handler.handleMessage(message);

        assertThat(capturedMessage).hasValue(message);
    }

    @Test
    void shouldThrowMessagePayloadValidationExceptionForInvalidPayload() {
        ValidationResult<Object> validationResult =
            ValidationResult.failure("value", List.of(new ViolationDetail("name", "must not be blank", "{message}", "", "NotBlank")));
        ExternalPayloadValidator validator = new StubExternalPayloadValidator(validationResult);
        AtomicReference<Message<?>> capturedMessage = new AtomicReference<>();
        MessageHandler delegate = capturedMessage::set;
        ValidatingPayloadMessageHandler handler = new ValidatingPayloadMessageHandler(delegate, validator);
        Message<String> message = MessageBuilder.withPayload("value").build();

        assertThatThrownBy(() -> handler.handleMessage(message))
            .isInstanceOfSatisfying(MessagePayloadValidationException.class, exception -> {
                assertThat(exception.getFailedMessage()).isSameAs(message);
                assertThat(exception.getValidationResult()).isEqualTo(validationResult);
            });
        assertThat(capturedMessage).hasValue(null);
    }

    @Test
    void shouldCreateDecoratingHandlerFromFactory() {
        ExternalPayloadValidator validator = new StubExternalPayloadValidator(ValidationResult.success("value"));
        MessageValidationHandlerFactory factory = new com.example.validation.messaging.internal.DefaultMessageValidationHandlerFactory(validator);
        MessageHandler delegate = message -> {
        };

        MessageHandler handler = factory.create(delegate);

        assertThat(handler).isInstanceOf(ValidatingPayloadMessageHandler.class);
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
