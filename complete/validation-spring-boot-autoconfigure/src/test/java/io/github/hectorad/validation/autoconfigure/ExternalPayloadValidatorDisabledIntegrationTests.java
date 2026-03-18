package io.github.hectorad.validation.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hectorad.validation.ExternalPayloadValidator;
import io.github.hectorad.validation.ValidationResult;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = ExternalPayloadValidatorDisabledIntegrationTests.TestApplication.class,
    properties = "hector.validation.enabled=false"
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

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
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
