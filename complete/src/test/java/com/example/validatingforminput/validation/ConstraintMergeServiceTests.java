package com.example.validatingforminput.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import jakarta.validation.constraints.Pattern;

class ConstraintMergeServiceTests {

	private final ConstraintMergeService mergeService = new ConstraintMergeService();

	@Test
	void shouldKeepStricterLowerBoundUsingMaxRule() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(
			false, false, NumericBound.inclusive(18L), null, null, null, List.of());
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getMin().setValue(16L);
		constraints.getMin().setHardValue(21L);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, constraints, "AnyClass", "anyField");

		assertBound(effective.min(), "21", true);
	}

	@Test
	void shouldKeepStricterUpperBoundUsingMinRule() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(
			false, false, null, NumericBound.inclusive(60L), null, null, List.of());
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getMax().setValue(70L);
		constraints.getMax().setHardValue(55L);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, constraints, "AnyClass", "anyField");

		assertBound(effective.max(), "55", true);
	}

	@Test
	void shouldMergeSizeWithMonotonicBounds() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(false, false, null, null, 2, 30, List.of());
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getSize().getMin().setValue(4L);
		constraints.getSize().getMax().setValue(40L);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, constraints, "AnyClass", "anyField");

		assertThat(effective.sizeMin()).isEqualTo(4);
		assertThat(effective.sizeMax()).isEqualTo(30);
	}

	@Test
	void shouldAddConfiguredPatternToBaselinePatterns() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(
			false,
			false,
			null,
			null,
			null,
			null,
			List.of(new PatternRule("^[A-Za-z ]+$", java.util.EnumSet.of(Pattern.Flag.CASE_INSENSITIVE)))
		);
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getPattern().setRegexes(List.of("^[A-Za-z]+$"));

		EffectiveFieldConstraints effective = mergeService.merge(baseline, constraints, "AnyClass", "anyField");

		assertThat(effective.patterns()).hasSize(2);
		assertThat(effective.patterns().get(0).regex()).isEqualTo("^[A-Za-z ]+$");
		assertThat(effective.patterns().get(1).regex()).isEqualTo("^[A-Za-z]+$");
	}

	@Test
	void shouldFailWhenEffectiveMinIsGreaterThanEffectiveMax() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(
			false, false, NumericBound.inclusive(18L), NumericBound.inclusive(60L), null, null, List.of());
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getMin().setValue(70L);
		constraints.getMax().setValue(50L);

		assertThatThrownBy(() -> mergeService.merge(baseline, constraints, "AnyClass", "anyField"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("effectiveMin > effectiveMax");
	}

	@Test
	void shouldChooseStricterDecimalLowerBoundAcrossIntegerAndDecimalSources() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(
			false, false, NumericBound.inclusive(18L), null, null, null, List.of());
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getMin().setValue(20L);
		constraints.getDecimalMin().setValue(new BigDecimal("20.5"));
		constraints.getDecimalMin().setInclusive(false);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, constraints, "AnyClass", "anyField");

		assertBound(effective.min(), "20.5", false);
	}

	@Test
	void shouldPreferExclusiveBoundWhenValuesMatch() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getDecimalMin().setValue(new BigDecimal("21.0"));
		constraints.getDecimalMin().setInclusive(true);
		constraints.getDecimalMin().setHardValue(new BigDecimal("21"));
		constraints.getDecimalMin().setHardInclusive(false);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, constraints, "AnyClass", "anyField");

		assertBound(effective.min(), "21", false);
	}

	@Test
	void shouldFailWhenEqualBoundsBecomeExclusive() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getDecimalMin().setValue(new BigDecimal("10"));
		constraints.getDecimalMin().setInclusive(false);
		constraints.getDecimalMax().setValue(new BigDecimal("10"));
		constraints.getDecimalMax().setInclusive(true);

		assertThatThrownBy(() -> mergeService.merge(baseline, constraints, "AnyClass", "anyField"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("equal bounds cannot be exclusive");
	}

	@Test
	void shouldFailWhenDecimalInclusiveFlagHasNoValue() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getDecimalMin().setInclusive(false);

		assertThatThrownBy(() -> mergeService.merge(baseline, constraints, "AnyClass", "anyField"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("decimal-min.inclusive requires decimal-min.value");
	}

	@Test
	void shouldFailWhenConfiguredPatternIsEmpty() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getPattern().setRegexes(List.of(""));

		assertThatThrownBy(() -> mergeService.merge(baseline, constraints, "AnyClass", "anyField"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("regex must be non-empty");
	}

	@Test
	void shouldFailWhenConfiguredPatternIsInvalid() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getPattern().setRegexes(List.of("["));

		assertThatThrownBy(() -> mergeService.merge(baseline, constraints, "AnyClass", "anyField"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("regex could not be compiled");
	}

	@Test
	void shouldFailWhenConfiguredSizeIsNegative() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getSize().getMin().setValue(-1L);

		assertThatThrownBy(() -> mergeService.merge(baseline, constraints, "AnyClass", "anyField"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("must be >= 0");
	}

	@Test
	void shouldFailWhenConfiguredSizeExceedsIntegerRange() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getSize().getMax().setHardValue(((long) Integer.MAX_VALUE) + 1L);

		assertThatThrownBy(() -> mergeService.merge(baseline, constraints, "AnyClass", "anyField"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("exceeds Integer.MAX_VALUE");
	}

	@Test
	void shouldAddConfiguredExtensionsRules() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		ValidationProperties.ExtensionRuleConstraint rule = new ValidationProperties.ExtensionRuleConstraint();
		rule.setJsonPath("$.partner.code");
		rule.setRegex("^[A-Z]{2}-\\d{3}$");
		constraints.getExtensions().setRules(List.of(rule));

		EffectiveFieldConstraints effective = mergeService.merge(baseline, constraints, "AnyClass", "extensions");

		assertThat(effective.extensionRules()).hasSize(1);
		assertThat(effective.extensionRules().get(0).jsonPath()).isEqualTo("$.partner.code");
		assertThat(effective.extensionRules().get(0).regex()).isEqualTo("^[A-Z]{2}-\\d{3}$");
	}

	@Test
	void shouldFailWhenConfiguredExtensionsJsonPathIsInvalid() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		ValidationProperties.ExtensionRuleConstraint rule = new ValidationProperties.ExtensionRuleConstraint();
		rule.setJsonPath("$..[");
		rule.setRegex("^[A-Z]+$");
		constraints.getExtensions().setRules(List.of(rule));

		assertThatThrownBy(() -> mergeService.merge(baseline, constraints, "AnyClass", "extensions"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("jsonPath could not be compiled");
	}

	@Test
	void shouldFailWhenConfiguredExtensionsRegexIsInvalid() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		ValidationProperties.ExtensionRuleConstraint rule = new ValidationProperties.ExtensionRuleConstraint();
		rule.setJsonPath("$.partner.code");
		rule.setRegex("[");
		constraints.getExtensions().setRules(List.of(rule));

		assertThatThrownBy(() -> mergeService.merge(baseline, constraints, "AnyClass", "extensions"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("regex could not be compiled");
	}

	private void assertBound(NumericBound bound, String expectedValue, boolean expectedInclusive) {
		assertThat(bound).isNotNull();
		assertThat(bound.value()).isEqualByComparingTo(new BigDecimal(expectedValue));
		assertThat(bound.inclusive()).isEqualTo(expectedInclusive);
	}
}
