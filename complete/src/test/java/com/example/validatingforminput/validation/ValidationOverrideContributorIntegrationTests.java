package com.example.validation.core.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.validatingforminput.ValidatingFormInputApplication;
import com.example.validation.core.api.ExternalPayloadValidator;
import com.example.validation.core.api.ValidationResult;
import com.example.validation.core.api.ViolationDetail;
import com.example.validation.core.spi.ClassValidationOverride;
import com.example.validation.core.spi.ConstraintOverrideSet;
import com.example.validation.core.spi.FieldValidationOverride;
import com.example.validation.core.spi.ValidationOverrideContributor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import jakarta.validation.constraints.Size;

import java.util.List;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ValidatingFormInputApplication.class)
@Import(ValidationOverrideContributorIntegrationTests.ValidationOverrideContributorTestConfiguration.class)
class ValidationOverrideContributorIntegrationTests {

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
	static class ValidationOverrideContributorTestConfiguration {

		@Bean
		ValidationOverrideContributor blankNameContributor() {
			return new ValidationOverrideContributor() {
				@Override
				public List<ClassValidationOverride> getValidationOverrides() {
					ConstraintOverrideSet constraints = new ConstraintOverrideSet();
					constraints.getNotBlank().setValue(true);
					constraints.getNotBlank().setMessage("Name must not be blank");
					return List.of(new ClassValidationOverride(
						ContributorTarget.class.getName(),
						List.of(new FieldValidationOverride("name", constraints))));
				}

				@Override
				public String sourceId() {
					return "test-contributor";
				}
			};
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
