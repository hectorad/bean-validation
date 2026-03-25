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
import jakarta.validation.constraints.Size;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = ValidatingFormInputApplication.class,
    properties = {
        "com.ampp.business-validation-override[0].full-class-name=com.example.validation.core.internal.ExternalPayloadValidatorIntegrationTests$PayloadTarget",
        "com.ampp.business-validation-override[0].fields[0].field-name=name",
        "com.ampp.business-validation-override[0].fields[0].constraints.size.min.value=5"
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
