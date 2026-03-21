package com.example.validatingforminput.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class ContributorMergeTests {

	private final ConstraintMergeService mergeService = new ConstraintMergeService();

	// --- Boolean constraints ---

	@Test
	void notNullTrueWinsOverFalse() {
		ConstraintOverrideSet first = overridesWith(new ConstraintOverrideSet.BooleanOverride(false, "ignored"), null,
			null, null, null, null, null, null, null);
		ConstraintOverrideSet second = overridesWith(new ConstraintOverrideSet.BooleanOverride(true, "Required"),
			null, null, null, null, null, null, null, null);

		ConstraintOverrideSet merged = mergeService.mergeOverrides(first, second);

		assertThat(merged.notNull().value()).isTrue();
		assertThat(merged.notNull().message()).isEqualTo("Required");
	}

	@Test
	void notNullTrueWinsRegardlessOfOrder() {
		ConstraintOverrideSet first = overridesWith(new ConstraintOverrideSet.BooleanOverride(true, "Required"),
			null, null, null, null, null, null, null, null);
		ConstraintOverrideSet second = overridesWith(new ConstraintOverrideSet.BooleanOverride(false, "ignored"),
			null, null, null, null, null, null, null, null);

		ConstraintOverrideSet merged = mergeService.mergeOverrides(first, second);

		assertThat(merged.notNull().value()).isTrue();
		assertThat(merged.notNull().message()).isEqualTo("Required");
	}

	@Test
	void notBlankTrueWinsOverNull() {
		ConstraintOverrideSet first = overridesWith(null, null, null, null, null, null, null, null, null);
		ConstraintOverrideSet second = overridesWith(null, new ConstraintOverrideSet.BooleanOverride(true, "Must have text"),
			null, null, null, null, null, null, null);

		ConstraintOverrideSet merged = mergeService.mergeOverrides(first, second);

		assertThat(merged.notBlank().value()).isTrue();
		assertThat(merged.notBlank().message()).isEqualTo("Must have text");
	}

	// --- Numeric bounds ---

	@Test
	void stricterMinWinsHigherValue() {
		ConstraintOverrideSet first = overridesWith(null, null,
			new ConstraintOverrideSet.NumericOverride(16L, "Min from first"), null,
			null, null, null, null, null);
		ConstraintOverrideSet second = overridesWith(null, null,
			new ConstraintOverrideSet.NumericOverride(18L, "Min from second"), null,
			null, null, null, null, null);

		ConstraintOverrideSet merged = mergeService.mergeOverrides(first, second);

		assertThat(merged.min().value()).isEqualTo(18L);
		assertThat(merged.min().message()).isEqualTo("Min from second");
	}

	@Test
	void stricterMaxWinsLowerValue() {
		ConstraintOverrideSet first = overridesWith(null, null, null,
			new ConstraintOverrideSet.NumericOverride(100L, "Max from first"),
			null, null, null, null, null);
		ConstraintOverrideSet second = overridesWith(null, null, null,
			new ConstraintOverrideSet.NumericOverride(50L, "Max from second"),
			null, null, null, null, null);

		ConstraintOverrideSet merged = mergeService.mergeOverrides(first, second);

		assertThat(merged.max().value()).isEqualTo(50L);
		assertThat(merged.max().message()).isEqualTo("Max from second");
	}

	@Test
	void minFromSingleContributorIsUsedWhenOtherHasNone() {
		ConstraintOverrideSet first = overridesWith(null, null,
			new ConstraintOverrideSet.NumericOverride(21L, "Must be adult"), null,
			null, null, null, null, null);
		ConstraintOverrideSet second = overridesWith(null, null, null, null, null, null, null, null, null);

		ConstraintOverrideSet merged = mergeService.mergeOverrides(first, second);

		assertThat(merged.min().value()).isEqualTo(21L);
		assertThat(merged.min().message()).isEqualTo("Must be adult");
	}

	// --- Decimal bounds ---

	@Test
	void stricterDecimalMinWinsHigherValue() {
		ConstraintOverrideSet first = overridesWith(null, null, null, null,
			new ConstraintOverrideSet.DecimalOverride(new BigDecimal("10.5"), true, "First"),
			null, null, null, null);
		ConstraintOverrideSet second = overridesWith(null, null, null, null,
			new ConstraintOverrideSet.DecimalOverride(new BigDecimal("12.0"), true, "Second"),
			null, null, null, null);

		ConstraintOverrideSet merged = mergeService.mergeOverrides(first, second);

		assertThat(merged.decimalMin().value()).isEqualByComparingTo(new BigDecimal("12.0"));
		assertThat(merged.decimalMin().message()).isEqualTo("Second");
	}

	@Test
	void stricterDecimalMaxWinsLowerValue() {
		ConstraintOverrideSet first = overridesWith(null, null, null, null, null,
			new ConstraintOverrideSet.DecimalOverride(new BigDecimal("99.9"), true, "First"),
			null, null, null);
		ConstraintOverrideSet second = overridesWith(null, null, null, null, null,
			new ConstraintOverrideSet.DecimalOverride(new BigDecimal("75.0"), true, "Second"),
			null, null, null);

		ConstraintOverrideSet merged = mergeService.mergeOverrides(first, second);

		assertThat(merged.decimalMax().value()).isEqualByComparingTo(new BigDecimal("75.0"));
		assertThat(merged.decimalMax().message()).isEqualTo("Second");
	}

	@Test
	void exclusiveDecimalBoundWinsOverInclusiveWhenValuesAreEqual() {
		ConstraintOverrideSet first = overridesWith(null, null, null, null,
			new ConstraintOverrideSet.DecimalOverride(new BigDecimal("18"), true, "Inclusive"),
			null, null, null, null);
		ConstraintOverrideSet second = overridesWith(null, null, null, null,
			new ConstraintOverrideSet.DecimalOverride(new BigDecimal("18"), false, "Exclusive"),
			null, null, null, null);

		ConstraintOverrideSet merged = mergeService.mergeOverrides(first, second);

		assertThat(merged.decimalMin().value()).isEqualByComparingTo(new BigDecimal("18"));
		assertThat(merged.decimalMin().inclusive()).isFalse();
		assertThat(merged.decimalMin().message()).isEqualTo("Exclusive");
	}

	// --- Size bounds ---

	@Test
	void stricterSizeMinWinsHigherValue() {
		ConstraintOverrideSet first = overridesWith(null, null, null, null, null, null,
			new ConstraintOverrideSet.SizeOverride(
				new ConstraintOverrideSet.NumericOverride(2L, "First"), null),
			null, null);
		ConstraintOverrideSet second = overridesWith(null, null, null, null, null, null,
			new ConstraintOverrideSet.SizeOverride(
				new ConstraintOverrideSet.NumericOverride(5L, "Second"), null),
			null, null);

		ConstraintOverrideSet merged = mergeService.mergeOverrides(first, second);

		assertThat(merged.size().min().value()).isEqualTo(5L);
		assertThat(merged.size().min().message()).isEqualTo("Second");
	}

	@Test
	void stricterSizeMaxWinsLowerValue() {
		ConstraintOverrideSet first = overridesWith(null, null, null, null, null, null,
			new ConstraintOverrideSet.SizeOverride(
				null, new ConstraintOverrideSet.NumericOverride(50L, "First")),
			null, null);
		ConstraintOverrideSet second = overridesWith(null, null, null, null, null, null,
			new ConstraintOverrideSet.SizeOverride(
				null, new ConstraintOverrideSet.NumericOverride(30L, "Second")),
			null, null);

		ConstraintOverrideSet merged = mergeService.mergeOverrides(first, second);

		assertThat(merged.size().max().value()).isEqualTo(30L);
		assertThat(merged.size().max().message()).isEqualTo("Second");
	}

	@Test
	void sizeMinAndMaxAreMergedIndependently() {
		ConstraintOverrideSet first = overridesWith(null, null, null, null, null, null,
			new ConstraintOverrideSet.SizeOverride(
				new ConstraintOverrideSet.NumericOverride(2L, null),
				new ConstraintOverrideSet.NumericOverride(30L, null)),
			null, null);
		ConstraintOverrideSet second = overridesWith(null, null, null, null, null, null,
			new ConstraintOverrideSet.SizeOverride(
				new ConstraintOverrideSet.NumericOverride(5L, null),
				new ConstraintOverrideSet.NumericOverride(50L, null)),
			null, null);

		ConstraintOverrideSet merged = mergeService.mergeOverrides(first, second);

		assertThat(merged.size().min().value()).isEqualTo(5L);  // max(2, 5) = 5
		assertThat(merged.size().max().value()).isEqualTo(30L); // min(30, 50) = 30
	}

	// --- Pattern overrides (additive) ---

	@Test
	void patternsFromBothContributorsAreCombined() {
		ConstraintOverrideSet first = overridesWith(null, null, null, null, null, null, null,
			new ConstraintOverrideSet.PatternOverride(List.of("^[A-Za-z]+$"), List.of(), null),
			null);
		ConstraintOverrideSet second = overridesWith(null, null, null, null, null, null, null,
			new ConstraintOverrideSet.PatternOverride(List.of("^\\d+$"), List.of(), null),
			null);

		ConstraintOverrideSet merged = mergeService.mergeOverrides(first, second);

		assertThat(merged.pattern().regexes()).containsExactly("^[A-Za-z]+$", "^\\d+$");
	}

	@Test
	void duplicatePatternsAreDeduplicatedOnMerge() {
		ConstraintOverrideSet first = overridesWith(null, null, null, null, null, null, null,
			new ConstraintOverrideSet.PatternOverride(List.of("^[A-Za-z]+$"), List.of(), "From first"),
			null);
		ConstraintOverrideSet second = overridesWith(null, null, null, null, null, null, null,
			new ConstraintOverrideSet.PatternOverride(List.of("^[A-Za-z]+$"), List.of(), "From second"),
			null);

		ConstraintOverrideSet merged = mergeService.mergeOverrides(first, second);

		assertThat(merged.pattern().regexes()).containsExactly("^[A-Za-z]+$");
	}

	@Test
	void patternFromOnlyOneContributorIsPassedThrough() {
		ConstraintOverrideSet first = overridesWith(null, null, null, null, null, null, null, null, null);
		ConstraintOverrideSet second = overridesWith(null, null, null, null, null, null, null,
			new ConstraintOverrideSet.PatternOverride(List.of("^\\d+$"), List.of(), "Digits only"),
			null);

		ConstraintOverrideSet merged = mergeService.mergeOverrides(first, second);

		assertThat(merged.pattern().regexes()).containsExactly("^\\d+$");
		assertThat(merged.pattern().message()).isEqualTo("Digits only");
	}

	// --- Extensions (additive) ---

	@Test
	void extensionRulesFromBothContributorsAreCombined() {
		ConstraintOverrideSet first = overridesWith(null, null, null, null, null, null, null, null,
			new ConstraintOverrideSet.ExtensionsOverride(List.of(
				new ConstraintOverrideSet.ExtensionRuleOverride("$.code", "^[A-Z]{2}$", null))));
		ConstraintOverrideSet second = overridesWith(null, null, null, null, null, null, null, null,
			new ConstraintOverrideSet.ExtensionsOverride(List.of(
				new ConstraintOverrideSet.ExtensionRuleOverride("$.type", "^\\d{3}$", null))));

		ConstraintOverrideSet merged = mergeService.mergeOverrides(first, second);

		assertThat(merged.extensions().rules()).hasSize(2);
		assertThat(merged.extensions().rules().get(0).jsonPath()).isEqualTo("$.code");
		assertThat(merged.extensions().rules().get(1).jsonPath()).isEqualTo("$.type");
	}

	// --- Null / EMPTY handling ---

	@Test
	void mergeWithNullSecondReturnsFirst() {
		ConstraintOverrideSet first = overridesWith(
			new ConstraintOverrideSet.BooleanOverride(true, "Required"),
			null, null, null, null, null, null, null, null);

		ConstraintOverrideSet merged = mergeService.mergeOverrides(first, null);

		assertThat(merged).isSameAs(first);
	}

	@Test
	void mergeWithNullFirstReturnsSecond() {
		ConstraintOverrideSet second = overridesWith(
			new ConstraintOverrideSet.BooleanOverride(true, "Required"),
			null, null, null, null, null, null, null, null);

		ConstraintOverrideSet merged = mergeService.mergeOverrides(null, second);

		assertThat(merged).isSameAs(second);
	}

	@Test
	void mergeWithBothNullReturnsEmpty() {
		ConstraintOverrideSet merged = mergeService.mergeOverrides(null, null);

		assertThat(merged).isSameAs(ConstraintOverrideSet.EMPTY);
	}

	// --- Integration: merged override applies correctly downstream ---

	@Test
	void mergedOverrideProducesCorrectEffectiveConstraints() {
		ConstraintOverrideSet first = overridesWith(
			new ConstraintOverrideSet.BooleanOverride(false, null), null,
			new ConstraintOverrideSet.NumericOverride(16L, null),
			new ConstraintOverrideSet.NumericOverride(100L, null),
			null, null, null, null, null);
		ConstraintOverrideSet second = overridesWith(
			new ConstraintOverrideSet.BooleanOverride(true, "Age required"), null,
			new ConstraintOverrideSet.NumericOverride(18L, "Must be adult"),
			new ConstraintOverrideSet.NumericOverride(65L, "Must be under 65"),
			null, null, null, null, null);

		ConstraintOverrideSet merged = mergeService.mergeOverrides(first, second);
		EffectiveFieldConstraints effective = mergeService.merge(
			BaselineFieldConstraints.empty(), merged, "AnyClass", "age");

		assertThat(effective.notNull()).isTrue();
		assertThat(effective.notNullMessage()).isEqualTo("Age required");
		assertThat(effective.min().value()).isEqualByComparingTo("18");
		assertThat(effective.minMessage()).isEqualTo("Must be adult");
		assertThat(effective.max().value()).isEqualByComparingTo("65");
		assertThat(effective.maxMessage()).isEqualTo("Must be under 65");
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
}
