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
	void shouldKeepStricterLowerBoundFromBaseline() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(
			false, false, NumericBound.inclusive(18L), null, null, null, List.of());
		ConstraintOverrideSet overrides = overridesWith(null, null,
			new ConstraintOverrideSet.NumericOverride(16L, null), null, null, null, null, null, null);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, overrides, "AnyClass", "anyField");

		assertBound(effective.min(), "18", true);
		assertThat(effective.minMessage()).isNull();
	}

	@Test
	void shouldKeepStricterUpperBoundFromBaseline() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(
			false, false, null, NumericBound.inclusive(60L), null, null, List.of());
		ConstraintOverrideSet overrides = overridesWith(null, null,
			null, new ConstraintOverrideSet.NumericOverride(70L, null), null, null, null, null, null);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, overrides, "AnyClass", "anyField");

		assertBound(effective.max(), "60", true);
		assertThat(effective.maxMessage()).isNull();
	}

	@Test
	void shouldMergeSizeWithMonotonicBounds() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(false, false, null, null, 2, 30, List.of());
		ConstraintOverrideSet overrides = overridesWith(null, null, null, null, null, null,
			new ConstraintOverrideSet.SizeOverride(
				new ConstraintOverrideSet.NumericOverride(4L, null),
				new ConstraintOverrideSet.NumericOverride(40L, null)),
			null, null);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, overrides, "AnyClass", "anyField");

		assertThat(effective.sizeMin()).isEqualTo(4);
		assertThat(effective.sizeMax()).isEqualTo(30);
		assertThat(effective.sizeMinMessage()).isNull();
		assertThat(effective.sizeMaxMessage()).isNull();
	}

	@Test
	void shouldAddConfiguredPatternToBaselinePatterns() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(
			false, false, null, null, null, null,
			List.of(new PatternRule("^[A-Za-z ]+$", java.util.EnumSet.of(Pattern.Flag.CASE_INSENSITIVE))));
		ConstraintOverrideSet overrides = overridesWith(null, null, null, null, null, null, null,
			new ConstraintOverrideSet.PatternOverride(List.of("^[A-Za-z]+$"), List.of(), null), null);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, overrides, "AnyClass", "anyField");

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
		ConstraintOverrideSet overrides = overridesWith(null, null,
			new ConstraintOverrideSet.NumericOverride(70L, null),
			new ConstraintOverrideSet.NumericOverride(50L, null),
			null, null, null, null, null);

		assertThatThrownBy(() -> mergeService.merge(baseline, overrides, "AnyClass", "anyField"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("effectiveMin > effectiveMax");
	}

	@Test
	void shouldChooseStricterDecimalLowerBoundAcrossIntegerAndDecimalSources() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(
			false, false, NumericBound.inclusive(18L), null, null, null, List.of());
		ConstraintOverrideSet overrides = overridesWith(null, null,
			new ConstraintOverrideSet.NumericOverride(20L, null), null,
			new ConstraintOverrideSet.DecimalOverride(new BigDecimal("20.5"), false, null),
			null, null, null, null);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, overrides, "AnyClass", "anyField");

		assertBound(effective.min(), "20.5", false);
		assertThat(effective.minMessage()).isNull();
	}

	@Test
	void shouldPreferExclusiveBoundWhenValuesMatch() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(
			false, false, NumericBound.inclusive(21L), null, null, null, List.of());
		ConstraintOverrideSet overrides = overridesWith(null, null, null, null,
			new ConstraintOverrideSet.DecimalOverride(new BigDecimal("21"), false, null),
			null, null, null, null);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, overrides, "AnyClass", "anyField");

		assertBound(effective.min(), "21", false);
		assertThat(effective.minMessage()).isNull();
	}

	@Test
	void shouldSetBooleanMessageWhenEnabledByConfiguredValue() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ConstraintOverrideSet overrides = overridesWith(
			new ConstraintOverrideSet.BooleanOverride(true, "Name is required"),
			new ConstraintOverrideSet.BooleanOverride(true, "Name must have text"),
			null, null, null, null, null, null, null);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, overrides, "AnyClass", "name");

		assertThat(effective.notNull()).isTrue();
		assertThat(effective.notNullMessage()).isEqualTo("Name is required");
		assertThat(effective.notBlank()).isTrue();
		assertThat(effective.notBlankMessage()).isEqualTo("Name must have text");
	}

	@Test
	void shouldNotSetBooleanMessageWhenOnlyBaselineEnablesConstraint() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(true, true, null, null, null, null, List.of());
		ConstraintOverrideSet overrides = overridesWith(
			new ConstraintOverrideSet.BooleanOverride(false, "ignored"),
			new ConstraintOverrideSet.BooleanOverride(false, "ignored"),
			null, null, null, null, null, null, null);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, overrides, "AnyClass", "name");

		assertThat(effective.notNull()).isTrue();
		assertThat(effective.notNullMessage()).isNull();
		assertThat(effective.notBlank()).isTrue();
		assertThat(effective.notBlankMessage()).isNull();
	}

	@Test
	void shouldUseWinningBoundMessageFromConfiguredSource() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(
			false, false, NumericBound.inclusive(18L), null, null, null, List.of());
		ConstraintOverrideSet overrides = overridesWith(null, null,
			new ConstraintOverrideSet.NumericOverride(20L, "Minimum age"), null,
			new ConstraintOverrideSet.DecimalOverride(new BigDecimal("21.5"), false, "Decimal minimum age"),
			null, null, null, null);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, overrides, "AnyClass", "age");

		assertBound(effective.min(), "21.5", false);
		assertThat(effective.minMessage()).isEqualTo("Decimal minimum age");
	}

	@Test
	void shouldKeepNullBoundMessageWhenBaselineWinsOnEqualBound() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(
			false, false, NumericBound.inclusive(18L), null, null, null, List.of());
		ConstraintOverrideSet overrides = overridesWith(null, null,
			new ConstraintOverrideSet.NumericOverride(18L, "Ignored message"), null,
			null, null, null, null, null);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, overrides, "AnyClass", "age");

		assertBound(effective.min(), "18", true);
		assertThat(effective.minMessage()).isNull();
	}

	@Test
	void shouldCarrySizeMessagesIndependently() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ConstraintOverrideSet overrides = overridesWith(null, null, null, null, null, null,
			new ConstraintOverrideSet.SizeOverride(
				new ConstraintOverrideSet.NumericOverride(3L, "Too short"),
				new ConstraintOverrideSet.NumericOverride(10L, "Too long")),
			null, null);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, overrides, "AnyClass", "name");

		assertThat(effective.sizeMin()).isEqualTo(3);
		assertThat(effective.sizeMinMessage()).isEqualTo("Too short");
		assertThat(effective.sizeMax()).isEqualTo(10);
		assertThat(effective.sizeMaxMessage()).isEqualTo("Too long");
	}

	@Test
	void shouldApplySharedConfiguredPatternMessageToEachConfiguredPattern() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(
			false, false, null, null, null, null,
			List.of(new PatternRule("^[A-Za-z ]+$", java.util.EnumSet.of(Pattern.Flag.CASE_INSENSITIVE))));
		ConstraintOverrideSet overrides = overridesWith(null, null, null, null, null, null, null,
			new ConstraintOverrideSet.PatternOverride(List.of("^[A-Za-z]+$"), List.of(), "Only letters are allowed"),
			null);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, overrides, "AnyClass", "name");

		assertThat(effective.patterns()).hasSize(2);
		assertThat(effective.patterns().get(0).message()).isNull();
		assertThat(effective.patterns().get(1).message()).isEqualTo("Only letters are allowed");
	}

	@Test
	void shouldDeduplicateConfiguredPatternThatMatchesBaselineIdentity() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(
			false, false, null, null, null, null,
			List.of(new PatternRule("^[A-Za-z]+$", java.util.EnumSet.of(Pattern.Flag.CASE_INSENSITIVE))));
		ConstraintOverrideSet overrides = overridesWith(null, null, null, null, null, null, null,
			new ConstraintOverrideSet.PatternOverride(
				List.of("^[A-Za-z]+$"), List.of("CASE_INSENSITIVE"), "Letters only"),
			null);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, overrides, "AnyClass", "name");

		assertThat(effective.patterns()).singleElement().satisfies(patternRule -> {
			assertThat(patternRule.regex()).isEqualTo("^[A-Za-z]+$");
			assertThat(patternRule.flags()).containsExactly(Pattern.Flag.CASE_INSENSITIVE);
			assertThat(patternRule.message()).isEqualTo("Letters only");
		});
	}

	@Test
	void shouldDeduplicateRepeatedConfiguredPatternsWithSameIdentity() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ConstraintOverrideSet overrides = overridesWith(null, null, null, null, null, null, null,
			new ConstraintOverrideSet.PatternOverride(
				List.of("^[A-Za-z]+$", "^[A-Za-z]+$"), List.of(), "Letters only"),
			null);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, overrides, "AnyClass", "name");

		assertThat(effective.patterns()).singleElement().satisfies(patternRule -> {
			assertThat(patternRule.regex()).isEqualTo("^[A-Za-z]+$");
			assertThat(patternRule.message()).isEqualTo("Letters only");
		});
	}

	@Test
	void shouldFailWhenEqualBoundsBecomeExclusive() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ConstraintOverrideSet overrides = overridesWith(null, null, null, null,
			new ConstraintOverrideSet.DecimalOverride(new BigDecimal("10"), false, null),
			new ConstraintOverrideSet.DecimalOverride(new BigDecimal("10"), true, null),
			null, null, null);

		assertThatThrownBy(() -> mergeService.merge(baseline, overrides, "AnyClass", "anyField"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("equal bounds cannot be exclusive");
	}

	@Test
	void shouldFailWhenDecimalInclusiveFlagHasNoValue() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ConstraintOverrideSet overrides = overridesWith(null, null, null, null,
			new ConstraintOverrideSet.DecimalOverride(null, false, null),
			null, null, null, null);

		assertThatThrownBy(() -> mergeService.merge(baseline, overrides, "AnyClass", "anyField"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("decimal-min.inclusive requires decimal-min.value");
	}

	@Test
	void shouldFailWhenConfiguredPatternIsEmpty() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ConstraintOverrideSet overrides = overridesWith(null, null, null, null, null, null, null,
			new ConstraintOverrideSet.PatternOverride(List.of(""), List.of(), null), null);

		assertThatThrownBy(() -> mergeService.merge(baseline, overrides, "AnyClass", "anyField"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("regex must be non-empty");
	}

	@Test
	void shouldFailWhenConfiguredPatternIsInvalid() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ConstraintOverrideSet overrides = overridesWith(null, null, null, null, null, null, null,
			new ConstraintOverrideSet.PatternOverride(List.of("["), List.of(), null), null);

		assertThatThrownBy(() -> mergeService.merge(baseline, overrides, "AnyClass", "anyField"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("regex could not be compiled");
	}

	@Test
	void shouldFailWhenConfiguredSizeIsNegative() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ConstraintOverrideSet overrides = overridesWith(null, null, null, null, null, null,
			new ConstraintOverrideSet.SizeOverride(
				new ConstraintOverrideSet.NumericOverride(-1L, null), null),
			null, null);

		assertThatThrownBy(() -> mergeService.merge(baseline, overrides, "AnyClass", "anyField"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("must be >= 0");
	}

	@Test
	void shouldFailWhenConfiguredSizeExceedsIntegerRange() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ConstraintOverrideSet overrides = overridesWith(null, null, null, null, null, null,
			new ConstraintOverrideSet.SizeOverride(
				null, new ConstraintOverrideSet.NumericOverride(((long) Integer.MAX_VALUE) + 1L, null)),
			null, null);

		assertThatThrownBy(() -> mergeService.merge(baseline, overrides, "AnyClass", "anyField"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("exceeds Integer.MAX_VALUE");
	}

	@Test
	void shouldAddConfiguredExtensionsRules() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ConstraintOverrideSet overrides = overridesWith(null, null, null, null, null, null, null, null,
			new ConstraintOverrideSet.ExtensionsOverride(List.of(
				new ConstraintOverrideSet.ExtensionRuleOverride("$.partner.code", "^[A-Z]{2}-\\d{3}$", null))));

		EffectiveFieldConstraints effective = mergeService.merge(baseline, overrides, "AnyClass", "extensions");

		assertThat(effective.extensionRules()).hasSize(1);
		assertThat(effective.extensionRules().getFirst().jsonPath()).isEqualTo("$.partner.code");
		assertThat(effective.extensionRules().getFirst().regex()).isEqualTo("^[A-Z]{2}-\\d{3}$");
		assertThat(effective.extensionRules().getFirst().message()).isNull();
	}

	@Test
	void shouldCarryConfiguredExtensionsRuleMessage() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ConstraintOverrideSet overrides = overridesWith(null, null, null, null, null, null, null, null,
			new ConstraintOverrideSet.ExtensionsOverride(List.of(
				new ConstraintOverrideSet.ExtensionRuleOverride("$.partner.code", "^[A-Z]{2}-\\d{3}$", "Invalid partner code"))));

		EffectiveFieldConstraints effective = mergeService.merge(baseline, overrides, "AnyClass", "extensions");

		assertThat(effective.extensionRules()).hasSize(1);
		assertThat(effective.extensionRules().getFirst().message()).isEqualTo("Invalid partner code");
	}

	@Test
	void shouldFailWhenConfiguredExtensionsJsonPathIsInvalid() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ConstraintOverrideSet overrides = overridesWith(null, null, null, null, null, null, null, null,
			new ConstraintOverrideSet.ExtensionsOverride(List.of(
				new ConstraintOverrideSet.ExtensionRuleOverride("$..[", "^[A-Z]+$", null))));

		assertThatThrownBy(() -> mergeService.merge(baseline, overrides, "AnyClass", "extensions"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("jsonPath could not be compiled");
	}

	@Test
	void shouldFailWhenConfiguredExtensionsRegexIsInvalid() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ConstraintOverrideSet overrides = overridesWith(null, null, null, null, null, null, null, null,
			new ConstraintOverrideSet.ExtensionsOverride(List.of(
				new ConstraintOverrideSet.ExtensionRuleOverride("$.partner.code", "[", null))));

		assertThatThrownBy(() -> mergeService.merge(baseline, overrides, "AnyClass", "extensions"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("regex could not be compiled");
	}

	@Test
	void shouldApplyConfiguredPatternFlags() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ConstraintOverrideSet overrides = overridesWith(null, null, null, null, null, null, null,
			new ConstraintOverrideSet.PatternOverride(
				List.of("^[a-z]+$"), List.of("CASE_INSENSITIVE", "MULTILINE"), null),
			null);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, overrides, "AnyClass", "anyField");

		assertThat(effective.patterns()).hasSize(1);
		assertThat(effective.patterns().getFirst().regex()).isEqualTo("^[a-z]+$");
		assertThat(effective.patterns().getFirst().flags()).containsExactlyInAnyOrder(
			Pattern.Flag.CASE_INSENSITIVE, Pattern.Flag.MULTILINE);
	}

	@Test
	void shouldFailWhenConfiguredPatternFlagIsInvalid() {
		BaselineFieldConstraints baseline = BaselineFieldConstraints.empty();
		ConstraintOverrideSet overrides = overridesWith(null, null, null, null, null, null, null,
			new ConstraintOverrideSet.PatternOverride(
				List.of("^[a-z]+$"), List.of("NOT_A_REAL_FLAG"), null),
			null);

		assertThatThrownBy(() -> mergeService.merge(baseline, overrides, "AnyClass", "anyField"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Invalid pattern flag")
			.hasMessageContaining("NOT_A_REAL_FLAG");
	}

	private static ConstraintOverrideSet overridesWith(
		ConstraintOverrideSet.BooleanOverride notNull,
		ConstraintOverrideSet.BooleanOverride notBlank,
		ConstraintOverrideSet.NumericOverride min,
		ConstraintOverrideSet.NumericOverride max,
		ConstraintOverrideSet.DecimalOverride decimalMin,
		ConstraintOverrideSet.DecimalOverride decimalMax,
		ConstraintOverrideSet.SizeOverride size,
		ConstraintOverrideSet.PatternOverride pattern,
		ConstraintOverrideSet.ExtensionsOverride extensions
	) {
		return new ConstraintOverrideSet(notNull, notBlank, min, max, decimalMin, decimalMax, size, pattern, extensions);
	}

	private void assertBound(NumericBound bound, String expectedValue, boolean expectedInclusive) {
		assertThat(bound).isNotNull();
		assertThat(bound.value()).isEqualByComparingTo(new BigDecimal(expectedValue));
		assertThat(bound.inclusive()).isEqualTo(expectedInclusive);
	}
}
