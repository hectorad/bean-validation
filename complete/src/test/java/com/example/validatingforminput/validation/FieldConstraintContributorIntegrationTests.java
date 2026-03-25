package com.example.validation.core.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.validatingforminput.ValidatingFormInputApplication;
import com.example.validation.core.api.ExternalPayloadValidator;
import com.example.validation.core.api.FieldConstraintSet;
import com.example.validation.core.api.NotBlankRule;
import com.example.validation.core.api.ValidationResult;
import com.example.validation.core.api.ViolationDetail;
import com.example.validation.core.spi.ConstraintContribution;
import com.example.validation.core.spi.FieldConstraintContributor;
import com.example.validation.core.spi.ValidationFieldContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import jakarta.validation.constraints.Size;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
	classes = ValidatingFormInputApplication.class,
	properties = {
		"com.ampp.business-validation-override[0].full-class-name=com.example.validation.core.internal.FieldConstraintContributorIntegrationTests$ContributorTarget",
		"com.ampp.business-validation-override[0].fields[0].field-name=name"
	})
@Import(FieldConstraintContributorIntegrationTests.FieldConstraintContributorTestConfiguration.class)
class FieldConstraintContributorIntegrationTests {

	@Autowired
	private ExternalPayloadValidator externalPayloadValidator;

	@Test
	void shouldApplyCustomContributorRulesWithoutReplacingCoreBeans() {
		ValidationResult<ContributorTarget> result = externalPayloadValidator.validate(new ContributorTarget(""));

		assertThat(result.valid()).isFalse();
		assertThat(result.violations())
			.singleElement()
			.extracting(ViolationDetail::message)
			.isEqualTo("Name must not be blank");
	}

	@TestConfiguration
	static class FieldConstraintContributorTestConfiguration {

		@Bean
		FieldConstraintContributor blankNameContributor() {
			return fieldContext -> contributesNotBlank(fieldContext)
				? java.util.Optional.of(new ConstraintContribution(
					"test-contributor",
					new FieldConstraintSet(java.util.List.of(new NotBlankRule("Name must not be blank")))))
				: java.util.Optional.empty();
		}

		private boolean contributesNotBlank(ValidationFieldContext fieldContext) {
			return ContributorTarget.class.getName().equals(fieldContext.declaringClassName())
				&& "name".equals(fieldContext.fieldName());
		}
	}

	static class ContributorTarget {

		@Size(max = 20)
		private final String name;

		ContributorTarget(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}
}
