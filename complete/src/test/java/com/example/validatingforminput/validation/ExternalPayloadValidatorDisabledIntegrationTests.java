package com.example.validation.core.internal;

import com.example.validation.core.api.*;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.example.validatingforminput.ValidatingFormInputApplication;

import jakarta.validation.constraints.NotBlank;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = ValidatingFormInputApplication.class,
    properties = {
        "com.ampp.validation-enabled=false"
    }
)
class ExternalPayloadValidatorDisabledIntegrationTests {

    @Autowired
    private ExternalPayloadValidator externalPayloadValidator;

    @Test
    void shouldReturnSuccessfulResultWhenValidationIsDisabled() {
        DisabledPayloadTarget target = new DisabledPayloadTarget();
        target.setName("");

        ValidationResult<DisabledPayloadTarget> result = externalPayloadValidator.validate(target);

        assertThat(result.valid()).isTrue();
        assertThat(result.value()).isSameAs(target);
        assertThat(result.violations()).isEmpty();
    }

    static class DisabledPayloadTarget {

        @NotBlank
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
