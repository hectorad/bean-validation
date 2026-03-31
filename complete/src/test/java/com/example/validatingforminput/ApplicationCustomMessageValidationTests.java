package com.example.validatingforminput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.example.validation.core.api.ExtensionsJsonPathRegex;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {
	"com.ampp.businessValidationOverride[0].fullClassName=com.example.validatingforminput.PersonForm",
	"com.ampp.businessValidationOverride[0].fields[0].fieldName=name",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[0].constraintType=NotNull",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[0].message=Name is required",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[1].constraintType=NotBlank",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[1].message=Name must not be blank",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[2].constraintType=Size",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[2].params.min=4",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[2].params.max=6",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[2].message=Name length must be between 4 and 6 characters",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[3].constraintType=Pattern",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[3].params.regexp=^[A-Za-z]+$",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[3].message=Name must contain letters only",
	"com.ampp.businessValidationOverride[0].fields[1].fieldName=age",
	"com.ampp.businessValidationOverride[0].fields[1].constraints[0].constraintType=Min",
	"com.ampp.businessValidationOverride[0].fields[1].constraints[0].params.value=25",
	"com.ampp.businessValidationOverride[0].fields[1].constraints[0].message=Age must be at least 25",
	"com.ampp.businessValidationOverride[0].fields[1].constraints[1].constraintType=Max",
	"com.ampp.businessValidationOverride[0].fields[1].constraints[1].params.value=59",
	"com.ampp.businessValidationOverride[0].fields[1].constraints[1].message=Age must be at most 59",
	"com.ampp.businessValidationOverride[0].fields[2].fieldName=salary",
	"com.ampp.businessValidationOverride[0].fields[2].constraints[0].constraintType=DecimalMin",
	"com.ampp.businessValidationOverride[0].fields[2].constraints[0].params.value=5000.50",
	"com.ampp.businessValidationOverride[0].fields[2].constraints[0].params.inclusive=false",
	"com.ampp.businessValidationOverride[0].fields[2].constraints[0].message=Salary must be greater than 5000.50",
	"com.ampp.businessValidationOverride[0].fields[2].constraints[1].constraintType=DecimalMax",
	"com.ampp.businessValidationOverride[0].fields[2].constraints[1].params.value=7500.75",
	"com.ampp.businessValidationOverride[0].fields[2].constraints[1].params.inclusive=true",
	"com.ampp.businessValidationOverride[0].fields[2].constraints[1].message=Salary must be at most 7500.75",
	"com.ampp.businessValidationOverride[0].fields[3].fieldName=extensions",
	"com.ampp.businessValidationOverride[0].fields[3].constraints[0].constraintType=Extensions",
	"com.ampp.businessValidationOverride[0].fields[3].constraints[0].params.jsonPath=$.vendorExtensionCode",
	"com.ampp.businessValidationOverride[0].fields[3].constraints[0].params.regexp=^[A-Z]{3}-[0-9]{4}$",
	"com.ampp.businessValidationOverride[0].fields[3].constraints[0].message=Vendor extension code is invalid"
})
class ApplicationCustomMessageValidationTests {

	@Autowired
	private PersonValidationService personValidationService;

	@Test
	void shouldUseCustomNotNullMessage() {
		PersonForm form = validBaseForm();
		form.setName(null);

		assertViolationMessage(captureViolations(form), "name", NotNull.class, "Name is required");
	}

	@Test
	void shouldUseCustomNotBlankMessage() {
		PersonForm form = validBaseForm();
		form.setName("   ");

		assertViolationMessage(captureViolations(form), "name", NotBlank.class, "Name must not be blank");
	}

	@Test
	void shouldUseSharedCustomSizeMessageForTooShortValues() {
		PersonForm form = validBaseForm();
		form.setName("Abc");

		assertViolationMessage(captureViolations(form), "name", Size.class, "Name length must be between 4 and 6 characters");
	}

	@Test
	void shouldUseSharedCustomSizeMessageForTooLongValues() {
		PersonForm form = validBaseForm();
		form.setName("ABCDEFG");

		assertViolationMessage(captureViolations(form), "name", Size.class, "Name length must be between 4 and 6 characters");
	}

	@Test
	void shouldUseCustomPatternMessage() {
		PersonForm form = validBaseForm();
		form.setName("John Doe");

		assertViolationMessage(captureViolations(form), "name", Pattern.class, "Name must contain letters only");
	}

	@Test
	void shouldUseCustomMinMessage() {
		PersonForm form = validBaseForm();
		form.setAge(24);

		assertViolationMessage(captureViolations(form), "age", DecimalMin.class, "Age must be at least 25");
	}

	@Test
	void shouldUseCustomMaxMessage() {
		PersonForm form = validBaseForm();
		form.setAge(60);

		assertViolationMessage(captureViolations(form), "age", DecimalMax.class, "Age must be at most 59");
	}

	@Test
	void shouldUseCustomDecimalMinMessage() {
		PersonForm form = validBaseForm();
		form.setSalary(new BigDecimal("5000.50"));

		assertViolationMessage(captureViolations(form), "salary", DecimalMin.class, "Salary must be greater than 5000.50");
	}

	@Test
	void shouldUseCustomDecimalMaxMessage() {
		PersonForm form = validBaseForm();
		form.setSalary(new BigDecimal("7500.76"));

		assertViolationMessage(captureViolations(form), "salary", DecimalMax.class, "Salary must be at most 7500.75");
	}

	@Test
	void shouldUseCustomExtensionsRuleMessage() {
		PersonForm form = validBaseForm();
		form.setExtensions(Map.of("vendorExtensionCode", "abc-1234"));

		assertViolationMessage(
			captureViolations(form),
			"extensions",
			ExtensionsJsonPathRegex.class,
			"Vendor extension code is invalid");
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
}
