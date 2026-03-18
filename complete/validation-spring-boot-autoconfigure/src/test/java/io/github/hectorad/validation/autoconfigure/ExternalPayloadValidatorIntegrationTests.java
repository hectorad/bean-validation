package io.github.hectorad.validation.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hectorad.validation.ExternalPayloadValidator;
import io.github.hectorad.validation.ValidationResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = ExternalPayloadValidatorIntegrationTests.TestApplication.class,
    properties = {
        "hector.validation.overrides[0].class-name=io.github.hectorad.validation.autoconfigure.ExternalPayloadValidatorIntegrationTests$PayloadTarget",
        "hector.validation.overrides[0].fields[0].field-name=name",
        "hector.validation.overrides[0].fields[0].constraints.size.min.value=5"
    }
)
class ExternalPayloadValidatorIntegrationTests {

    @Autowired
    private ExternalPayloadValidator externalPayloadValidator;

    @Test
    void shouldReturnMergedConfiguredViolations() {
        PayloadTarget target = new PayloadTarget();
        target.setName("abc");

        ValidationResult<PayloadTarget> result = externalPayloadValidator.validate(target);

        assertThat(result.valid()).isFalse();
        assertThat(result.value()).isSameAs(target);
        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().getFirst().propertyPath()).isEqualTo("name");
        assertThat(result.violations().getFirst().constraintType()).isEqualTo(Size.class.getName());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    static class PayloadTarget {

        @NotBlank
        @Size(min = 3, max = 10)
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
