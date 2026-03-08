package com.example.validatingforminput.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import org.springframework.stereotype.Component;

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
			toSizeInteger(effectiveConfig.getSize().getMin().getValue(), className, fieldName, "size.min.value"),
			toSizeInteger(effectiveConfig.getSize().getMin().getHardValue(), className, fieldName, "size.min.hardValue"));
		Integer sizeMax = strictestUpperBound(
			baseline.sizeMax(),
			toSizeInteger(effectiveConfig.getSize().getMax().getValue(), className, fieldName, "size.max.value"),
			toSizeInteger(effectiveConfig.getSize().getMax().getHardValue(), className, fieldName, "size.max.hardValue"));

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
		appendConfiguredPatterns(patterns, effectiveConfig.getPattern().getRegexes(), className, fieldName);

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

	private void appendConfiguredPatterns(
		List<PatternRule> patterns,
		List<String> configuredRegexes,
		String className,
		String fieldName
	) {
		for (int index = 0; index < configuredRegexes.size(); index++) {
			String regex = configuredRegexes.get(index);
			if (regex == null || regex.isEmpty()) {
				throw new InvalidConstraintConfigurationException(
					"Invalid pattern constraint. regex must be non-empty for class="
						+ className + ", field=" + fieldName + ", index=" + index);
			}
			validateRegex(regex, className, fieldName, index);
			patterns.add(new PatternRule(regex, null));
		}
	}

	private void validateRegex(String regex, String className, String fieldName, int index) {
		try {
			java.util.regex.Pattern.compile(regex);
		}
		catch (PatternSyntaxException exception) {
			throw new InvalidConstraintConfigurationException(
				"Invalid pattern constraint. regex could not be compiled for class="
					+ className + ", field=" + fieldName + ", index=" + index + ", regex=" + regex,
				exception);
		}
	}

	private Integer toSizeInteger(Long value, String className, String fieldName, String propertyName) {
		if (value == null) {
			return null;
		}
		if (value < 0) {
			throw new InvalidConstraintConfigurationException(
				"Invalid size constraint. " + propertyName + " must be >= 0 for class="
					+ className + ", field=" + fieldName + ", value=" + value);
		}
		try {
			return Math.toIntExact(value);
		}
		catch (ArithmeticException exception) {
			throw new InvalidConstraintConfigurationException(
				"Invalid size constraint. " + propertyName + " exceeds Integer.MAX_VALUE for class="
					+ className + ", field=" + fieldName + ", value=" + value,
				exception);
		}
	}
}
