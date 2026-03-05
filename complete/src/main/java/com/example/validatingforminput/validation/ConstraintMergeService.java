package com.example.validatingforminput.validation;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ConstraintMergeService {

	public EffectiveFieldConstraints merge(
		BaselineFieldConstraints baseline,
		ValidationProperties.Constraints configuredConstraints,
		String className,
		String fieldName
	) {
		ValidationProperties.Constraints effectiveConfig =
			(configuredConstraints == null) ? new ValidationProperties.Constraints() : configuredConstraints;

		boolean notNull = baseline.notNull()
			|| isTrue(effectiveConfig.getNotNull().getValue())
			|| isTrue(effectiveConfig.getNotNull().getHardValue());
		boolean notBlank = baseline.notBlank()
			|| isTrue(effectiveConfig.getNotBlank().getValue())
			|| isTrue(effectiveConfig.getNotBlank().getHardValue());

		Long min = strictestLowerBound(
			baseline.min(),
			effectiveConfig.getMin().getValue(),
			effectiveConfig.getMin().getHardValue());
		Long max = strictestUpperBound(
			baseline.max(),
			effectiveConfig.getMax().getValue(),
			effectiveConfig.getMax().getHardValue());

		Integer sizeMin = strictestLowerBound(
			baseline.sizeMin(),
			toInteger(effectiveConfig.getSize().getMin().getValue()),
			toInteger(effectiveConfig.getSize().getMin().getHardValue()));
		Integer sizeMax = strictestUpperBound(
			baseline.sizeMax(),
			toInteger(effectiveConfig.getSize().getMax().getValue()),
			toInteger(effectiveConfig.getSize().getMax().getHardValue()));

		if (min != null && max != null && min > max) {
			throw new InvalidConstraintConfigurationException(
				"Invalid numeric constraints. effectiveMin > effectiveMax for class="
					+ className + ", field=" + fieldName + ", effectiveMin=" + min + ", effectiveMax=" + max);
		}
		if (sizeMin != null && sizeMax != null && sizeMin > sizeMax) {
			throw new InvalidConstraintConfigurationException(
				"Invalid size constraints. effectiveSizeMin > effectiveSizeMax for class="
					+ className + ", field=" + fieldName + ", effectiveSizeMin=" + sizeMin + ", effectiveSizeMax=" + sizeMax);
		}

		List<PatternRule> patterns = new ArrayList<>(baseline.patterns());
		for (String regex : effectiveConfig.getPattern().getRegexes()) {
			if (StringUtils.hasText(regex)) {
				patterns.add(new PatternRule(regex, null));
			}
		}

		return new EffectiveFieldConstraints(notNull, notBlank, min, max, sizeMin, sizeMax, patterns);
	}

	private boolean isTrue(Boolean value) {
		return Boolean.TRUE.equals(value);
	}

	private <N extends Number & Comparable<N>> N strictestLowerBound(N baseline, N configured, N hard) {
		N result = baseline;
		if (configured != null) {
			result = (result == null) ? configured : max(result, configured);
		}
		if (hard != null) {
			result = (result == null) ? hard : max(result, hard);
		}
		return result;
	}

	private <N extends Number & Comparable<N>> N strictestUpperBound(N baseline, N configured, N hard) {
		N result = baseline;
		if (configured != null) {
			result = (result == null) ? configured : min(result, configured);
		}
		if (hard != null) {
			result = (result == null) ? hard : min(result, hard);
		}
		return result;
	}

	private <N extends Comparable<N>> N max(N first, N second) {
		return (first.compareTo(second) >= 0) ? first : second;
	}

	private <N extends Comparable<N>> N min(N first, N second) {
		return (first.compareTo(second) <= 0) ? first : second;
	}

	private Integer toInteger(Long value) {
		return (value == null) ? null : Math.toIntExact(value);
	}
}
