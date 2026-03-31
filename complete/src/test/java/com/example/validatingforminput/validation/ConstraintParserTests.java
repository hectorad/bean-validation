package com.example.validation.core.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

class ConstraintParserTests {

	@Test
	void shouldParseNotNullConstraint() {
		assertThat(ConstraintParser.parse(constraint("NotNull", params -> {}, "Field is required")))
			.isEqualTo(new ConstraintParser.ParsedConstraint(
				ConstraintParser.ConstraintType.NOT_NULL,
				"Field is required",
				null,
				null,
				null,
				null,
				null,
				null,
				null));
	}

	@Test
	void shouldParseNotBlankConstraint() {
		assertThat(ConstraintParser.parse(constraint("NotBlank", params -> {}, "Field must not be blank")))
			.isEqualTo(new ConstraintParser.ParsedConstraint(
				ConstraintParser.ConstraintType.NOT_BLANK,
				"Field must not be blank",
				null,
				null,
				null,
				null,
				null,
				null,
				null));
	}

	@Test
	void shouldParseMinConstraint() {
		assertThat(ConstraintParser.parse(constraint("Min", params -> params.setValue(BigDecimal.valueOf(18)), "Must be at least 18")))
			.isEqualTo(new ConstraintParser.ParsedConstraint(
				ConstraintParser.ConstraintType.MIN,
				"Must be at least 18",
				18L,
				null,
				null,
				null,
				null,
				null,
				null));
	}

	@Test
	void shouldParseMaxConstraint() {
		assertThat(ConstraintParser.parse(constraint("Max", params -> params.setValue(BigDecimal.valueOf(99)), "Must be at most 99")))
			.isEqualTo(new ConstraintParser.ParsedConstraint(
				ConstraintParser.ConstraintType.MAX,
				"Must be at most 99",
				99L,
				null,
				null,
				null,
				null,
				null,
				null));
	}

	@Test
	void shouldParseDecimalMinConstraint() {
		assertThat(ConstraintParser.parse(constraint("DecimalMin", params -> {
			params.setValue(new BigDecimal("5.50"));
			params.setInclusive(false);
		}, "Must be greater than 5.50")))
			.isEqualTo(new ConstraintParser.ParsedConstraint(
				ConstraintParser.ConstraintType.DECIMAL_MIN,
				"Must be greater than 5.50",
				null,
				new BigDecimal("5.50"),
				false,
				null,
				null,
				null,
				null));
	}

	@Test
	void shouldParseDecimalMaxConstraintPreservingNullInclusive() {
		assertThat(ConstraintParser.parse(constraint("DecimalMax", params -> params.setValue(new BigDecimal("10.75")), "Must be at most 10.75")))
			.isEqualTo(new ConstraintParser.ParsedConstraint(
				ConstraintParser.ConstraintType.DECIMAL_MAX,
				"Must be at most 10.75",
				null,
				new BigDecimal("10.75"),
				null,
				null,
				null,
				null,
				null));
	}

	@Test
	void shouldParseSizeConstraint() {
		assertThat(ConstraintParser.parse(constraint("Size", params -> {
			params.setMin(3L);
			params.setMax(20L);
		}, "Invalid length")))
			.isEqualTo(new ConstraintParser.ParsedConstraint(
				ConstraintParser.ConstraintType.SIZE,
				"Invalid length",
				null,
				null,
				null,
				3L,
				20L,
				null,
				null));
	}

	@Test
	void shouldParsePatternConstraint() {
		assertThat(ConstraintParser.parse(constraint("Pattern", params -> params.setRegexp("^[A-Za-z]+$"), "Letters only")))
			.isEqualTo(new ConstraintParser.ParsedConstraint(
				ConstraintParser.ConstraintType.PATTERN,
				"Letters only",
				null,
				null,
				null,
				null,
				null,
				"^[A-Za-z]+$",
				null));
	}

	@Test
	void shouldParseExtensionsConstraint() {
		assertThat(ConstraintParser.parse(constraint("Extensions", params -> {
			params.setJsonPath("$.vendorCode");
			params.setRegexp("^[A-Z]{3}$");
		}, "Invalid vendor code")))
			.isEqualTo(new ConstraintParser.ParsedConstraint(
				ConstraintParser.ConstraintType.EXTENSIONS,
				"Invalid vendor code",
				null,
				null,
				null,
				null,
				null,
				"^[A-Z]{3}$",
				"$.vendorCode"));
	}

	@Test
	void shouldRejectUnknownConstraintType() {
		assertThatThrownBy(() -> ConstraintParser.parse(constraint("FooBar", params -> {})))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Unsupported constraintType: FooBar");
	}

	@Test
	void shouldRejectMissingNumericValue() {
		assertThatThrownBy(() -> ConstraintParser.parse(constraint("Min", params -> {})))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("params.value must be provided");
	}

	@Test
	void shouldRejectNonIntegerNumericValue() {
		assertThatThrownBy(() -> ConstraintParser.parse(
			constraint("Max", params -> params.setValue(new BigDecimal("25.5")))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("params.value must be an integer");
	}

	@Test
	void shouldRejectPatternWithoutRegexp() {
		assertThatThrownBy(() -> ConstraintParser.parse(constraint("Pattern", params -> {})))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Pattern requires params.regexp");
	}

	@Test
	void shouldRejectExtensionsWithoutJsonPath() {
		assertThatThrownBy(() -> ConstraintParser.parse(
			constraint("Extensions", params -> params.setRegexp(".*"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Extensions requires params.jsonPath");
	}

	@Test
	void shouldRejectExtensionsWithoutRegexp() {
		assertThatThrownBy(() -> ConstraintParser.parse(
			constraint("Extensions", params -> params.setJsonPath("$.status"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Extensions requires params.regexp");
	}

	@Test
	void shouldRejectSizeWithoutBounds() {
		assertThatThrownBy(() -> ConstraintParser.parse(constraint("Size", params -> {})))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Size requires params.min or params.max");
	}

	private ValidationProperties.ConstraintMapping constraint(
		String constraintType,
		Consumer<ValidationProperties.ConstraintParameters> customizer
	) {
		return constraint(constraintType, customizer, null);
	}

	private ValidationProperties.ConstraintMapping constraint(
		String constraintType,
		Consumer<ValidationProperties.ConstraintParameters> customizer,
		String message
	) {
		ValidationProperties.ConstraintMapping constraint = new ValidationProperties.ConstraintMapping();
		constraint.setConstraintType(constraintType);
		ValidationProperties.ConstraintParameters params = new ValidationProperties.ConstraintParameters();
		customizer.accept(params);
		constraint.setParams(params);
		constraint.setMessage(message);
		return constraint;
	}
}
