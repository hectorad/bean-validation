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
		assertThat(effective.minMessage()).isNull();
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
		assertThat(effective.maxMessage()).isNull();
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
		assertThat(effective.sizeMinMessage()).isNull();
		assertThat(effective.sizeMaxMessage()).isNull();
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
		assertThat(effective.patterns().get(0).message()).isNull();
		assertThat(effective.patterns().get(1).message()).isNull();
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
		assertThat(effective.minMessage()).isNull();
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
		assertThat(effective.minMessage()).isNull();
	}

	@Test
	void shouldSetBooleanMessageWhenEnabledByConfiguredValue() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getNotNull().setValue(true);
		constraints.getNotNull().setMessage("Name is required");
		constraints.getNotBlank().setHardValue(true);
		constraints.getNotBlank().setMessage("Name must have text");

		EffectiveFieldConstraints effective = mergeService.merge(baseline, constraints, "AnyClass", "name");

		assertThat(effective.notNull()).isTrue();
		assertThat(effective.notNullMessage()).isEqualTo("Name is required");
		assertThat(effective.notBlank()).isTrue();
		assertThat(effective.notBlankMessage()).isEqualTo("Name must have text");
	}

	@Test
	void shouldNotSetBooleanMessageWhenOnlyBaselineEnablesConstraint() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(true, true, null, null, null, null, List.of());
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getNotNull().setValue(false);
		constraints.getNotNull().setMessage("ignored");
		constraints.getNotBlank().setHardValue(false);
		constraints.getNotBlank().setMessage("ignored");

		EffectiveFieldConstraints effective = mergeService.merge(baseline, constraints, "AnyClass", "name");

		assertThat(effective.notNull()).isTrue();
		assertThat(effective.notNullMessage()).isNull();
		assertThat(effective.notBlank()).isTrue();
		assertThat(effective.notBlankMessage()).isNull();
	}

	@Test
	void shouldUseWinningBoundMessageFromConfiguredSource() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(
			false, false, NumericBound.inclusive(18L), null, null, null, List.of());
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getMin().setValue(20L);
		constraints.getMin().setMessage("Minimum age");
		constraints.getDecimalMin().setValue(new BigDecimal("21.5"));
		constraints.getDecimalMin().setInclusive(false);
		constraints.getDecimalMin().setMessage("Decimal minimum age");

		EffectiveFieldConstraints effective = mergeService.merge(baseline, constraints, "AnyClass", "age");

		assertBound(effective.min(), "21.5", false);
		assertThat(effective.minMessage()).isEqualTo("Decimal minimum age");
	}

	@Test
	void shouldKeepNullBoundMessageWhenBaselineWinsOnEqualBound() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(
			false, false, NumericBound.inclusive(18L), null, null, null, List.of());
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getMin().setValue(18L);
		constraints.getMin().setMessage("Ignored message");

		EffectiveFieldConstraints effective = mergeService.merge(baseline, constraints, "AnyClass", "age");

		assertBound(effective.min(), "18", true);
		assertThat(effective.minMessage()).isNull();
	}

	@Test
	void shouldCarrySizeMessagesIndependently() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getSize().getMin().setValue(3L);
		constraints.getSize().getMin().setMessage("Too short");
		constraints.getSize().getMax().setValue(10L);
		constraints.getSize().getMax().setMessage("Too long");

		EffectiveFieldConstraints effective = mergeService.merge(baseline, constraints, "AnyClass", "name");

		assertThat(effective.sizeMin()).isEqualTo(3);
		assertThat(effective.sizeMinMessage()).isEqualTo("Too short");
		assertThat(effective.sizeMax()).isEqualTo(10);
		assertThat(effective.sizeMaxMessage()).isEqualTo("Too long");
	}

	@Test
	void shouldApplySharedConfiguredPatternMessageToEachConfiguredPattern() {
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
		constraints.getPattern().setMessage("Only letters are allowed");

		EffectiveFieldConstraints effective = mergeService.merge(baseline, constraints, "AnyClass", "name");

		assertThat(effective.patterns()).hasSize(2);
		assertThat(effective.patterns().get(0).message()).isNull();
		assertThat(effective.patterns().get(1).message()).isEqualTo("Only letters are allowed");
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
		assertThat(effective.extensionRules().getFirst().jsonPath()).isEqualTo("$.partner.code");
		assertThat(effective.extensionRules().getFirst().regex()).isEqualTo("^[A-Z]{2}-\\d{3}$");
		assertThat(effective.extensionRules().getFirst().message()).isNull();
	}

	@Test
	void shouldCarryConfiguredExtensionsRuleMessage() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		ValidationProperties.ExtensionRuleConstraint rule = new ValidationProperties.ExtensionRuleConstraint();
		rule.setJsonPath("$.partner.code");
		rule.setRegex("^[A-Z]{2}-\\d{3}$");
		rule.setMessage("Invalid partner code");
		constraints.getExtensions().setRules(List.of(rule));

		EffectiveFieldConstraints effective = mergeService.merge(baseline, constraints, "AnyClass", "extensions");

		assertThat(effective.extensionRules()).hasSize(1);
		assertThat(effective.extensionRules().getFirst().message()).isEqualTo("Invalid partner code");
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

	@Test
	void shouldApplyConfiguredPatternFlags() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getPattern().setRegexes(List.of("^[a-z]+$"));
		constraints.getPattern().setFlags(List.of("CASE_INSENSITIVE", "MULTILINE"));

		EffectiveFieldConstraints effective = mergeService.merge(baseline, constraints, "AnyClass", "anyField");

		assertThat(effective.patterns()).hasSize(1);
		assertThat(effective.patterns().getFirst().regex()).isEqualTo("^[a-z]+$");
		assertThat(effective.patterns().getFirst().flags()).containsExactlyInAnyOrder(
			Pattern.Flag.CASE_INSENSITIVE, Pattern.Flag.MULTILINE);
	}

	@Test
	void shouldFailWhenConfiguredPatternFlagIsInvalid() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getPattern().setRegexes(List.of("^[a-z]+$"));
		constraints.getPattern().setFlags(List.of("NOT_A_REAL_FLAG"));

		assertThatThrownBy(() -> mergeService.merge(baseline, constraints, "AnyClass", "anyField"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Invalid pattern flag")
			.hasMessageContaining("NOT_A_REAL_FLAG");
	}

	@Test
	void shouldMergeMultipleConstraintSourcesUsingStrictestValues() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(
			false,
			false,
			NumericBound.inclusive(18L),
			NumericBound.inclusive(60L),
			2,
			30,
			List.of());

		ValidationProperties.Constraints first = new ValidationProperties.Constraints();
		first.getMin().setValue(20L);
		first.getMin().setMessage("Min from first");
		first.getMax().setValue(59L);
		first.getMax().setMessage("Max from first");
		first.getSize().getMin().setValue(4L);
		first.getSize().getMin().setMessage("Size min from first");
		first.getSize().getMax().setValue(35L);

		ValidationProperties.Constraints second = new ValidationProperties.Constraints();
		second.getDecimalMin().setHardValue(new BigDecimal("21.5"));
		second.getDecimalMin().setHardInclusive(false);
		second.getDecimalMin().setMessage("Min from second");
		second.getMax().setHardValue(55L);
		second.getMax().setMessage("Max from second");
		second.getSize().getMin().setHardValue(5L);
		second.getSize().getMin().setMessage("Size min from second");

		EffectiveFieldConstraints effective = mergeService.merge(
			baseline,
			List.of(first, second),
			"AnyClass",
			"anyField");

		assertBound(effective.min(), "21.5", false);
		assertThat(effective.minMessage()).isEqualTo("Min from second");
		assertBound(effective.max(), "55", true);
		assertThat(effective.maxMessage()).isEqualTo("Max from second");
		assertThat(effective.sizeMin()).isEqualTo(5);
		assertThat(effective.sizeMinMessage()).isEqualTo("Size min from second");
		assertThat(effective.sizeMax()).isEqualTo(30);
		assertThat(effective.sizeMaxMessage()).isNull();
	}

	@Test
	void shouldKeepFirstContributorMessageWhenStrictnessTies() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();

		ValidationProperties.Constraints first = new ValidationProperties.Constraints();
		first.getMin().setValue(25L);
		first.getMin().setMessage("First message");

		ValidationProperties.Constraints second = new ValidationProperties.Constraints();
		second.getMin().setHardValue(25L);
		second.getMin().setMessage("Second message");

		EffectiveFieldConstraints effective = mergeService.merge(
			baseline,
			List.of(first, second),
			"AnyClass",
			"anyField");

		assertBound(effective.min(), "25", true);
		assertThat(effective.minMessage()).isEqualTo("First message");
	}

	@Test
	void shouldAllowLaterHardBoundToWinWhenItIsStricter() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();

		ValidationProperties.Constraints first = new ValidationProperties.Constraints();
		first.getMin().setValue(25L);
		first.getMin().setMessage("First message");

		ValidationProperties.Constraints second = new ValidationProperties.Constraints();
		second.getDecimalMin().setHardValue(new BigDecimal("25.5"));
		second.getDecimalMin().setHardInclusive(false);
		second.getDecimalMin().setMessage("Second message");

		EffectiveFieldConstraints effective = mergeService.merge(
			baseline,
			List.of(first, second),
			"AnyClass",
			"anyField");

		assertBound(effective.min(), "25.5", false);
		assertThat(effective.minMessage()).isEqualTo("Second message");
	}

	@Test
	void shouldFailWhenMultiSourceEffectiveMinIsGreaterThanEffectiveMax() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();

		ValidationProperties.Constraints first = new ValidationProperties.Constraints();
		first.getMin().setValue(70L);

		ValidationProperties.Constraints second = new ValidationProperties.Constraints();
		second.getMax().setValue(50L);

		assertThatThrownBy(() -> mergeService.merge(
			baseline,
			List.of(first, second),
			"AnyClass",
			"anyField"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("effectiveMin > effectiveMax");
	}

	private void assertBound(NumericBound bound, String expectedValue, boolean expectedInclusive) {
		assertThat(bound).isNotNull();
		assertThat(bound.value()).isEqualByComparingTo(new BigDecimal(expectedValue));
		assertThat(bound.inclusive()).isEqualTo(expectedInclusive);
	}
}
