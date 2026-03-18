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

import com.example.validatingforminput.validation.ExtensionsJsonPathRegex;

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
	"com.ampp.business-validation-override[0].full-class-name=com.example.validatingforminput.PersonForm",
	"com.ampp.business-validation-override[0].fields[0].field-name=name",
	"com.ampp.business-validation-override[0].fields[0].constraints.not-null.value=true",
	"com.ampp.business-validation-override[0].fields[0].constraints.not-null.message=Name is required",
	"com.ampp.business-validation-override[0].fields[0].constraints.not-blank.value=true",
	"com.ampp.business-validation-override[0].fields[0].constraints.not-blank.message=Name must not be blank",
	"com.ampp.business-validation-override[0].fields[0].constraints.size.min.value=4",
	"com.ampp.business-validation-override[0].fields[0].constraints.size.min.message=Name must have at least 4 characters",
	"com.ampp.business-validation-override[0].fields[0].constraints.size.max.value=6",
	"com.ampp.business-validation-override[0].fields[0].constraints.size.max.message=Name must have at most 6 characters",
	"com.ampp.business-validation-override[0].fields[0].constraints.pattern.regexes[0]=^[A-Za-z]+$",
	"com.ampp.business-validation-override[0].fields[0].constraints.pattern.message=Name must contain letters only",
	"com.ampp.business-validation-override[0].fields[1].field-name=age",
	"com.ampp.business-validation-override[0].fields[1].constraints.min.value=25",
	"com.ampp.business-validation-override[0].fields[1].constraints.min.message=Age must be at least 25",
	"com.ampp.business-validation-override[0].fields[1].constraints.max.value=59",
	"com.ampp.business-validation-override[0].fields[1].constraints.max.message=Age must be at most 59",
	"com.ampp.business-validation-override[0].fields[2].field-name=salary",
	"com.ampp.business-validation-override[0].fields[2].constraints.decimal-min.value=5000.50",
	"com.ampp.business-validation-override[0].fields[2].constraints.decimal-min.inclusive=false",
	"com.ampp.business-validation-override[0].fields[2].constraints.decimal-min.message=Salary must be greater than 5000.50",
	"com.ampp.business-validation-override[0].fields[2].constraints.decimal-max.value=7500.75",
	"com.ampp.business-validation-override[0].fields[2].constraints.decimal-max.inclusive=true",
	"com.ampp.business-validation-override[0].fields[2].constraints.decimal-max.message=Salary must be at most 7500.75",
	"com.ampp.business-validation-override[0].fields[3].field-name=extensions",
	"com.ampp.business-validation-override[0].fields[3].constraints.extensions.rules[0].json-path=$.vendorExtensionCode",
	"com.ampp.business-validation-override[0].fields[3].constraints.extensions.rules[0].regex=^[A-Z]{3}-[0-9]{4}$",
	"com.ampp.business-validation-override[0].fields[3].constraints.extensions.rules[0].message=Vendor extension code is invalid"
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
	void shouldUseCustomSizeMinMessage() {
		PersonForm form = validBaseForm();
		form.setName("Abc");

		assertViolationMessage(captureViolations(form), "name", Size.class, "Name must have at least 4 characters");
	}

	@Test
	void shouldUseCustomSizeMaxMessage() {
		PersonForm form = validBaseForm();
		form.setName("ABCDEFG");

		assertViolationMessage(captureViolations(form), "name", Size.class, "Name must have at most 6 characters");
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
