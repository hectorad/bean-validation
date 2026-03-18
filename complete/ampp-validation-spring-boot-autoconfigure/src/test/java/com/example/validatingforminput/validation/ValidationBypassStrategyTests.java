package com.example.validatingforminput.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.validation.autoconfigure.ValidationAutoConfiguration;

class ValidationBypassStrategyTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class));

    @Test
    void bypassStrategyBeanNotCreatedByDefault() {
        contextRunner
            .withPropertyValues("com.ampp.validation-enabled=true")
            .run(context -> assertThat(context).doesNotHaveBean(ValidationBypassStrategy.class));
    }

    @Test
    void bypassStrategyBeanNotCreatedWhenPropertyDisabled() {
        contextRunner
            .withPropertyValues(
                "com.ampp.validation-enabled=true",
                "com.ampp.request-validation-bypass.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(ValidationBypassStrategy.class));
    }

    @Test
    void bypassStrategyBeanCreatedWhenPropertyEnabled() {
        contextRunner
            .withPropertyValues(
                "com.ampp.validation-enabled=true",
                "com.ampp.request-validation-bypass.enabled=true")
            .run(context -> {
                assertThat(context).hasSingleBean(ValidationBypassStrategy.class);
                assertThat(context.getBean(ValidationBypassStrategy.class))
                    .isInstanceOf(RequestHeaderValidationBypassStrategy.class);
            });
    }

    @Test
    void customBypassStrategyTakesPrecedence() {
        contextRunner
            .withUserConfiguration(CustomBypassStrategyConfiguration.class)
            .withPropertyValues(
                "com.ampp.validation-enabled=true",
                "com.ampp.request-validation-bypass.enabled=true")
            .run(context -> {
                assertThat(context).hasSingleBean(ValidationBypassStrategy.class);
                assertThat(context.getBean(ValidationBypassStrategy.class))
                    .isInstanceOf(TestBypassStrategy.class);
            });
    }

    @Test
    void validatorFactoryBeanWorksWithoutBypassStrategy() {
        contextRunner
            .withPropertyValues("com.ampp.validation-enabled=true")
            .run(context -> {
                assertThat(context).doesNotHaveBean(ValidationBypassStrategy.class);
                assertThat(context).hasBean("defaultValidator");
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomBypassStrategyConfiguration {

        @Bean
        ValidationBypassStrategy customBypassStrategy() {
            return new TestBypassStrategy();
        }
    }

    static class TestBypassStrategy implements ValidationBypassStrategy {

        @Override
        public boolean shouldBypass() {
            return false;
        }
    }
}
