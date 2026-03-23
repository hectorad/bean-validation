package com.example.validatingforminput.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.example.validatingforminput.ValidatingFormInputApplication;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Payload;
import jakarta.validation.Validator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
	classes = ValidatingFormInputApplication.class,
	properties = {
		"com.ampp.business-validation-override[0].full-class-name=com.example.validatingforminput.validation.ValidationMetadataPreservationIntegrationTests$EmailTarget",
		"com.ampp.business-validation-override[0].fields[0].field-name=email",
		"com.ampp.business-validation-override[0].fields[0].constraints.not-null.value=true",
		"com.ampp.business-validation-override[1].full-class-name=com.example.validatingforminput.validation.ValidationMetadataPreservationIntegrationTests$CascadeTarget",
		"com.ampp.business-validation-override[1].fields[0].field-name=contact",
		"com.ampp.business-validation-override[1].fields[0].constraints.not-null.value=true",
		"com.ampp.business-validation-override[2].full-class-name=com.example.validatingforminput.validation.ValidationMetadataPreservationIntegrationTests$PatternTarget",
		"com.ampp.business-validation-override[2].fields[0].field-name=name",
		"com.ampp.business-validation-override[2].fields[0].constraints.pattern.regexes[0]=^[A-Za-z ]+$",
		"com.ampp.business-validation-override[2].fields[0].constraints.pattern.message=Configured letters only",
		"com.ampp.business-validation-override[3].full-class-name=com.example.validatingforminput.validation.ValidationMetadataPreservationIntegrationTests$CustomAttributeTarget",
		"com.ampp.business-validation-override[3].fields[0].field-name=code",
		"com.ampp.business-validation-override[3].fields[0].constraints.not-null.value=true"
	})
class ValidationMetadataPreservationIntegrationTests {

	@Autowired
	private Validator validator;

	@Test
	void shouldPreservePassthroughConstraintOnConfiguredField() {
		EmailTarget target = new EmailTarget();
		target.setEmail("not-an-email");

		Set<ConstraintViolation<EmailTarget>> violations = validator.validate(target);

		assertThat(violations).hasSize(1);
		assertThat(findViolation(violations, "email", Email.class).getMessageTemplate())
			.isEqualTo("{jakarta.validation.constraints.Email.message}");
	}

	@Test
	void shouldPreserveCascadeValidationOnConfiguredField() {
		CascadeTarget target = new CascadeTarget();
		Contact contact = new Contact();
		contact.setName(" ");
		target.setContact(contact);

		Set<ConstraintViolation<CascadeTarget>> violations = validator.validate(target);

		assertThat(findViolation(violations, "name", NotBlank.class)).isNotNull();
	}

	@Test
	void shouldEmitSinglePatternViolationForBaselineEquivalentConfiguredPattern() {
		PatternTarget target = new PatternTarget();
		target.setName("John1");

		Set<ConstraintViolation<PatternTarget>> violations = validator.validate(target);

		assertThat(violations).hasSize(1);
		assertThat(findViolation(violations, "name", Pattern.class).getMessage())
			.isEqualTo("Configured letters only");
	}

	@Test
	void shouldPreserveCustomPassthroughConstraintAttributesOnConfiguredField() {
		CustomAttributeTarget target = new CustomAttributeTarget();
		target.setCode("WRONG");

		Set<ConstraintViolation<CustomAttributeTarget>> violations = validator.validate(target);

		assertThat(violations).hasSize(1);
		ConstraintViolation<CustomAttributeTarget> violation = findViolation(violations, "code", AllowedPrefix.class);
		assertThat(violation.getMessage()).isEqualTo("must start with ID-");
		assertThat(((AllowedPrefix) violation.getConstraintDescriptor().getAnnotation()).prefix()).isEqualTo("ID-");
	}

	private <T> ConstraintViolation<T> findViolation(
		Set<ConstraintViolation<T>> violations,
		String propertySuffix,
		Class<? extends Annotation> annotationType
	) {
		return violations.stream()
			.filter(candidate -> {
				String propertyPath = candidate.getPropertyPath().toString();
				return propertyPath.equals(propertySuffix) || propertyPath.endsWith("." + propertySuffix);
			})
			.filter(candidate -> candidate.getConstraintDescriptor().getAnnotation().annotationType().equals(annotationType))
			.findFirst()
			.orElseThrow(() -> new AssertionError(
				"Missing violation for property=" + propertySuffix + ", annotation=" + annotationType.getSimpleName()));
	}

	static class EmailTarget {

		@Email
		private String email;

		public String getEmail() {
			return email;
		}

		public void setEmail(String email) {
			this.email = email;
		}
	}

	static class CascadeTarget {

		@Valid
		@NotNull
		private Contact contact;

		public Contact getContact() {
			return contact;
		}

		public void setContact(Contact contact) {
			this.contact = contact;
		}
	}

	static class Contact {

		@NotBlank
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	static class PatternTarget {

		@Pattern(regexp = "^[A-Za-z ]+$", message = "root level")
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	static class CustomAttributeTarget {

		@AllowedPrefix(prefix = "ID-", message = "must start with ID-")
		private String code;

		public String getCode() {
			return code;
		}

		public void setCode(String code) {
			this.code = code;
		}
	}

	@Documented
	@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE })
	@Retention(RetentionPolicy.RUNTIME)
	@Constraint(validatedBy = AllowedPrefixValidator.class)
	@interface AllowedPrefix {

		String message() default "must start with configured prefix";

		Class<?>[] groups() default {};

		Class<? extends Payload>[] payload() default {};

		String prefix();
	}

	static class AllowedPrefixValidator implements ConstraintValidator<AllowedPrefix, String> {

		private String prefix;

		@Override
		public void initialize(AllowedPrefix constraintAnnotation) {
			this.prefix = constraintAnnotation.prefix();
		}

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			return value == null || value.startsWith(prefix);
		}
	}
}
