package com.example.validatingforminput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.example.validatingforminput.validation.FieldConstraintContributor;
import com.example.validatingforminput.validation.ValidationProperties;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {
	"com.ampp.business-validation-override[0].full-class-name=com.example.validatingforminput.PersonForm",
	"com.ampp.business-validation-override[0].fields[0].field-name=age",
	"com.ampp.business-validation-override[0].fields[0].constraints.min.value=25",
	"com.ampp.business-validation-override[0].fields[0].constraints.min.message=Age must be at least 25 (properties)",
	"com.ampp.business-validation-override[0].fields[1].field-name=salary",
	"com.ampp.business-validation-override[0].fields[1].constraints.decimal-min.value=2000.00",
	"com.ampp.business-validation-override[0].fields[1].constraints.decimal-min.inclusive=false",
	"com.ampp.business-validation-override[0].fields[1].constraints.decimal-min.message=Salary must be greater than 2000.00 (properties)"
})
@Import(ApplicationMultiContributorValidationTests.MultiContributorTestConfiguration.class)
class ApplicationMultiContributorValidationTests {

	private static final String PERSON_FORM_CLASS = "com.example.validatingforminput.PersonForm";

	private static final String AGE_FIELD = "age";

	private static final String SALARY_FIELD = "salary";

	@Autowired
	private PersonValidationService personValidationService;

	@Test
	void shouldAllowLowerOrderContributorToWinExactTieAgainstProperties() {
		PersonForm form = validBaseForm();
		form.setAge(24);

		assertViolationMessage(
			captureViolations(form),
			AGE_FIELD,
			DecimalMin.class,
			"Age must be at least 25 (ordered contributor)");
	}

	@Test
	void shouldLetPropertiesWinExactTieAgainstUnorderedContributor() {
		PersonForm form = validBaseForm();
		form.setSalary(new BigDecimal("2000.00"));

		assertViolationMessage(
			captureViolations(form),
			SALARY_FIELD,
			DecimalMin.class,
			"Salary must be greater than 2000.00 (properties)");
	}

	@Test
	void shouldApplyStricterHardMaxFromLaterContributor() {
		PersonForm form = validBaseForm();
		form.setAge(56);

		assertViolationMessage(
			captureViolations(form),
			AGE_FIELD,
			DecimalMax.class,
			"Age must be at most 55 (second contributor)");
	}

	private Set<ConstraintViolation<?>> captureViolations(PersonForm form) {
		ConstraintViolationException exception =
			catchThrowableOfType(() -> personValidationService.validate(form), ConstraintViolationException.class);
		assertThat(exception).isNotNull();
		return exception.getConstraintViolations();
	}

	private void assertViolationMessage(
		Set<ConstraintViolation<?>> violations,
		String fieldName,
		Class<? extends Annotation> annotationType,
		String expectedMessage
	) {
		ConstraintViolation<?> violation = violations.stream()
			.filter(candidate -> matchesFieldPath(candidate.getPropertyPath().toString(), fieldName))
			.filter(candidate -> candidate.getConstraintDescriptor().getAnnotation().annotationType().equals(annotationType))
			.findFirst()
			.orElseThrow(() -> new AssertionError(
				"Missing violation for field=" + fieldName + ", annotation=" + annotationType.getSimpleName()));

		assertThat(violation.getMessage()).isEqualTo(expectedMessage);
	}

	private boolean matchesFieldPath(String propertyPath, String fieldName) {
		return propertyPath.equals(fieldName) || propertyPath.endsWith("." + fieldName);
	}

	private PersonForm validBaseForm() {
		PersonForm form = new PersonForm();
		form.setName("Robert");
		form.setAge(30);
		form.setSalary(new BigDecimal("6000.00"));
		form.setExtensions(Map.of("vendorExtensionCode", "ABC-1234"));
		return form;
	}

	@TestConfiguration
	static class MultiContributorTestConfiguration {

		@Bean
		@Order(-1)
		FieldConstraintContributor orderedAgeContributor() {
			return (className, fieldName, baseline) -> {
				if (!PERSON_FORM_CLASS.equals(className) || !AGE_FIELD.equals(fieldName)) {
					return Optional.empty();
				}
				ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
				constraints.getMin().setValue(25L);
				constraints.getMin().setMessage("Age must be at least 25 (ordered contributor)");
				constraints.getMax().setValue(58L);
				constraints.getMax().setMessage("Age must be at most 58 (ordered contributor)");
				return Optional.of(constraints);
			};
		}

		@Bean
		@Order(2)
		FieldConstraintContributor secondAgeContributor() {
			return (className, fieldName, baseline) -> {
				if (!PERSON_FORM_CLASS.equals(className) || !AGE_FIELD.equals(fieldName)) {
					return Optional.empty();
				}
				ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
				constraints.getMax().setHardValue(55L);
				constraints.getMax().setMessage("Age must be at most 55 (second contributor)");
				return Optional.of(constraints);
			};
		}

		@Bean
		FieldConstraintContributor unorderedSalaryContributor() {
			return (className, fieldName, baseline) -> {
				if (!PERSON_FORM_CLASS.equals(className) || !SALARY_FIELD.equals(fieldName)) {
					return Optional.empty();
				}
				ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
				constraints.getDecimalMin().setHardValue(new BigDecimal("2000.00"));
				constraints.getDecimalMin().setHardInclusive(false);
				constraints.getDecimalMin().setMessage("Salary must be greater than 2000.00 (unordered contributor)");
				return Optional.of(constraints);
			};
		}
	}
}
