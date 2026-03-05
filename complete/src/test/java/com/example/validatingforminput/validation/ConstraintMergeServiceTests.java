package com.example.validatingforminput.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import jakarta.validation.constraints.Pattern;

class ConstraintMergeServiceTests {

	private final ConstraintMergeService mergeService = new ConstraintMergeService();

	@Test
	void shouldKeepStricterLowerBoundUsingMaxRule() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(false, false, 18L, null, null, null, List.of());
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getMin().setValue(16L);
		constraints.getMin().setHardValue(21L);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, constraints, "AnyClass", "anyField");

		assertThat(effective.min()).isEqualTo(21L);
	}

	@Test
	void shouldKeepStricterUpperBoundUsingMinRule() {
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(false, false, null, 60L, null, null, List.of());
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getMax().setValue(70L);
		constraints.getMax().setHardValue(55L);

		EffectiveFieldConstraints effective = mergeService.merge(baseline, constraints, "AnyClass", "anyField");

		assertThat(effective.max()).isEqualTo(55L);
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
		BaselineFieldConstraints baseline = new BaselineFieldConstraints(false, false, 18L, 60L, null, null, List.of());
		ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
		constraints.getMin().setValue(70L);
		constraints.getMax().setValue(50L);

		assertThatThrownBy(() -> mergeService.merge(baseline, constraints, "AnyClass", "anyField"))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("effectiveMin > effectiveMax");
	}
}
