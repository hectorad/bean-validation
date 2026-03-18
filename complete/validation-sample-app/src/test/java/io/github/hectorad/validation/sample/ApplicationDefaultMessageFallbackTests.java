package io.github.hectorad.validation.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {
	"hector.validation.overrides[0].class-name=io.github.hectorad.validation.sample.PersonForm",
	"hector.validation.overrides[0].fields[0].field-name=name",
	"hector.validation.overrides[0].fields[0].constraints.pattern.regexes[0]=^[A-Za-z]+$",
	"hector.validation.overrides[0].fields[1].field-name=age",
	"hector.validation.overrides[0].fields[1].constraints.min.value=25"
})
class ApplicationDefaultMessageFallbackTests {

	@Autowired
	private PersonValidationService personValidationService;

	@Test
	void shouldKeepDefaultPatternMessageTemplateWhenNoCustomMessageConfigured() {
		PersonForm form = validBaseForm();
		form.setName("John Doe");

		ConstraintViolation<?> violation = findViolation(captureViolations(form), "name", Pattern.class);
		assertThat(violation.getMessageTemplate()).isEqualTo("{jakarta.validation.constraints.Pattern.message}");
	}

	@Test
	void shouldKeepDefaultDecimalMinMessageTemplateWhenNoCustomMessageConfigured() {
		PersonForm form = validBaseForm();
		form.setAge(24);

		ConstraintViolation<?> violation = findViolation(captureViolations(form), "age", DecimalMin.class);
		assertThat(violation.getMessageTemplate()).isEqualTo("{jakarta.validation.constraints.DecimalMin.message}");
	}

	private Set<ConstraintViolation<?>> captureViolations(PersonForm form) {
		ConstraintViolationException exception =
			catchThrowableOfType(() -> personValidationService.validate(form), ConstraintViolationException.class);
		assertThat(exception).isNotNull();
		return exception.getConstraintViolations();
	}

	private ConstraintViolation<?> findViolation(
		Set<ConstraintViolation<?>> violations,
		String fieldName,
		Class<? extends Annotation> annotationType
	) {
		return violations.stream()
			.filter(candidate -> matchesFieldPath(candidate.getPropertyPath().toString(), fieldName))
			.filter(candidate -> candidate.getConstraintDescriptor().getAnnotation().annotationType().equals(annotationType))
			.findFirst()
			.orElseThrow(() -> new AssertionError(
				"Missing violation for field=" + fieldName + ", annotation=" + annotationType.getSimpleName()));
	}

	private boolean matchesFieldPath(String propertyPath, String fieldName) {
		return propertyPath.equals(fieldName) || propertyPath.endsWith("." + fieldName);
	}

	private PersonForm validBaseForm() {
		PersonForm form = new PersonForm();
		form.setName("Robert");
		form.setAge(30);
		form.setSalary(new BigDecimal("2000.00"));
		return form;
	}
}
