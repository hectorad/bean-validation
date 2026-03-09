package com.example.validatingforminput.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class ExtensionsJsonPathRegexValidatorTests {

	private static final String CODE_REGEX = "^[A-Z]{2}-\\d{3}$";

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void shouldAcceptMatchingScalarValue() {
		ScalarExtensionsTarget target = new ScalarExtensionsTarget(Map.of("partner", Map.of("code", "AB-123")));

		assertThat(validate(target)).isEmpty();
	}

	@Test
	void shouldSkipNullFieldValue() {
		ScalarExtensionsTarget target = new ScalarExtensionsTarget(null);

		assertThat(validate(target)).isEmpty();
	}

	@Test
	void shouldRejectNonMatchingScalarValue() {
		ScalarExtensionsTarget target = new ScalarExtensionsTarget(Map.of("partner", Map.of("code", "ab-123")));

		assertThat(validate(target)).hasSize(1);
	}

	@Test
	void shouldSkipMissingPath() {
		ScalarExtensionsTarget target = new ScalarExtensionsTarget(Map.of("partner", Map.of("name", "Acme")));

		assertThat(validate(target)).isEmpty();
	}

	@Test
	void shouldSkipResolvedNullValue() {
		Map<String, Object> partner = new HashMap<>();
		partner.put("code", null);
		ScalarExtensionsTarget target = new ScalarExtensionsTarget(Map.of("partner", partner));

		assertThat(validate(target)).isEmpty();
	}

	@Test
	void shouldSkipBlankJsonString() {
		ScalarExtensionsTarget target = new ScalarExtensionsTarget("   ");

		assertThat(validate(target)).isEmpty();
	}

	@Test
	void shouldRejectInvalidJsonString() {
		ScalarExtensionsTarget target = new ScalarExtensionsTarget("{ not-json");

		assertThat(validate(target)).hasSize(1);
	}

	@Test
	void shouldAcceptMultipleMatchingScalarValues() {
		MultiValueExtensionsTarget target = new MultiValueExtensionsTarget(Map.of(
			"items",
			List.of(
				Map.of("code", "AB-123"),
				Map.of("code", "CD-456"))));

		assertThat(validate(target)).isEmpty();
	}

	@Test
	void shouldRejectNonScalarMatchedValue() {
		NonScalarExtensionsTarget target = new NonScalarExtensionsTarget(Map.of("partner", Map.of("code", "AB-123")));

		assertThat(validate(target)).hasSize(1);
	}

	private <T> Set<ConstraintViolation<T>> validate(T target) {
		return validator.validate(target);
	}

	private static final class ScalarExtensionsTarget {

		@ExtensionsJsonPathRegex(jsonPath = "$.partner.code", regex = CODE_REGEX)
		private final Object extensions;

		private ScalarExtensionsTarget(Object extensions) {
			this.extensions = extensions;
		}
	}

	private static final class MultiValueExtensionsTarget {

		@ExtensionsJsonPathRegex(jsonPath = "$.items[*].code", regex = CODE_REGEX)
		private final Object extensions;

		private MultiValueExtensionsTarget(Object extensions) {
			this.extensions = extensions;
		}
	}

	private static final class NonScalarExtensionsTarget {

		@ExtensionsJsonPathRegex(jsonPath = "$.partner", regex = CODE_REGEX)
		private final Object extensions;

		private NonScalarExtensionsTarget(Object extensions) {
			this.extensions = extensions;
		}
	}
}
