package com.example.validatingforminput.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.example.validatingforminput.ValidatingFormInputApplication;

import jakarta.validation.ConstraintViolation;
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
		"com.ampp.business-validation-override[2].fields[0].constraints.pattern.message=Configured letters only"
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
}
