package com.example.validatingforminput.validation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
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

		NumericBound min = strictestLowerBound(
			baseline.min(),
			toInclusiveBound(effectiveConfig.getMin().getValue()),
			toInclusiveBound(effectiveConfig.getMin().getHardValue()),
			toDecimalBound(
				effectiveConfig.getDecimalMin().getValue(),
				effectiveConfig.getDecimalMin().getInclusive(),
				className,
				fieldName,
				"decimal-min.value",
				"decimal-min.inclusive"),
			toDecimalBound(
				effectiveConfig.getDecimalMin().getHardValue(),
				effectiveConfig.getDecimalMin().getHardInclusive(),
				className,
				fieldName,
				"decimal-min.hard-value",
				"decimal-min.hard-inclusive"));
		NumericBound max = strictestUpperBound(
			baseline.max(),
			toInclusiveBound(effectiveConfig.getMax().getValue()),
			toInclusiveBound(effectiveConfig.getMax().getHardValue()),
			toDecimalBound(
				effectiveConfig.getDecimalMax().getValue(),
				effectiveConfig.getDecimalMax().getInclusive(),
				className,
				fieldName,
				"decimal-max.value",
				"decimal-max.inclusive"),
			toDecimalBound(
				effectiveConfig.getDecimalMax().getHardValue(),
				effectiveConfig.getDecimalMax().getHardInclusive(),
				className,
				fieldName,
				"decimal-max.hard-value",
				"decimal-max.hard-inclusive"));

		Integer sizeMin = strictestComparableLowerBound(
			baseline.sizeMin(),
			toSizeInteger(effectiveConfig.getSize().getMin().getValue(), className, fieldName, "size.min.value"),
			toSizeInteger(effectiveConfig.getSize().getMin().getHardValue(), className, fieldName, "size.min.hardValue"));
		Integer sizeMax = strictestComparableUpperBound(
			baseline.sizeMax(),
			toSizeInteger(effectiveConfig.getSize().getMax().getValue(), className, fieldName, "size.max.value"),
			toSizeInteger(effectiveConfig.getSize().getMax().getHardValue(), className, fieldName, "size.max.hardValue"));

		validateNumericBounds(min, max, className, fieldName);
		if (sizeMin != null && sizeMax != null && sizeMin > sizeMax) {
			throw new InvalidConstraintConfigurationException(
				"Invalid size constraints. effectiveSizeMin > effectiveSizeMax for class="
					+ className + ", field=" + fieldName + ", effectiveSizeMin=" + sizeMin + ", effectiveSizeMax=" + sizeMax);
		}

		List<PatternRule> patterns = new ArrayList<>(baseline.patterns());
		appendConfiguredPatterns(patterns, effectiveConfig.getPattern().getRegexes(), className, fieldName);
		List<ExtensionRegexRule> extensionRules =
			toConfiguredExtensionRules(effectiveConfig.getExtensions().getRules(), className, fieldName);

		return new EffectiveFieldConstraints(notNull, notBlank, min, max, sizeMin, sizeMax, patterns, extensionRules);
	}

	private boolean isTrue(Boolean value) {
		return Boolean.TRUE.equals(value);
	}

	private NumericBound strictestLowerBound(NumericBound... bounds) {
		NumericBound result = null;
		for (NumericBound bound : bounds) {
			result = NumericBound.stricterLower(result, bound);
		}
		return result;
	}

	private NumericBound strictestUpperBound(NumericBound... bounds) {
		NumericBound result = null;
		for (NumericBound bound : bounds) {
			result = NumericBound.stricterUpper(result, bound);
		}
		return result;
	}

	@SafeVarargs
	private final <N extends Comparable<N>> N strictestComparableLowerBound(N... bounds) {
		N result = null;
		for (N bound : bounds) {
			if (bound == null) {
				continue;
			}
			if (result == null || bound.compareTo(result) > 0) {
				result = bound;
			}
		}
		return result;
	}

	@SafeVarargs
	private final <N extends Comparable<N>> N strictestComparableUpperBound(N... bounds) {
		N result = null;
		for (N bound : bounds) {
			if (bound == null) {
				continue;
			}
			if (result == null || bound.compareTo(result) < 0) {
				result = bound;
			}
		}
		return result;
	}

	private NumericBound toInclusiveBound(Long value) {
		return (value == null) ? null : NumericBound.inclusive(value);
	}

	private NumericBound toDecimalBound(
		BigDecimal value,
		Boolean inclusive,
		String className,
		String fieldName,
		String valuePropertyName,
		String inclusivePropertyName
	) {
		if (value == null) {
			if (inclusive != null) {
				throw new InvalidConstraintConfigurationException(
					"Invalid decimal constraint. " + inclusivePropertyName + " requires " + valuePropertyName
						+ " for class=" + className + ", field=" + fieldName);
			}
			return null;
		}
		return new NumericBound(value, inclusive == null || inclusive);
	}

	private void validateNumericBounds(
		NumericBound min,
		NumericBound max,
		String className,
		String fieldName
	) {
		if (min == null || max == null) {
			return;
		}

		int comparison = min.value().compareTo(max.value());
		if (comparison > 0) {
			throw new InvalidConstraintConfigurationException(
				"Invalid numeric constraints. effectiveMin > effectiveMax for class="
					+ className + ", field=" + fieldName + ", effectiveMin=" + render(min) + ", effectiveMax=" + render(max));
		}
		if (comparison == 0 && (!min.inclusive() || !max.inclusive())) {
			throw new InvalidConstraintConfigurationException(
				"Invalid numeric constraints. equal bounds cannot be exclusive for class="
					+ className + ", field=" + fieldName + ", effectiveMin=" + render(min) + ", effectiveMax=" + render(max));
		}
	}

	private String render(NumericBound bound) {
		return bound.value().toPlainString() + " (inclusive=" + bound.inclusive() + ")";
	}

	private void appendConfiguredPatterns(
		List<PatternRule> patterns,
		List<String> configuredRegexes,
		String className,
		String fieldName
	) {
		for (int index = 0; index < configuredRegexes.size(); index++) {
			patterns.add(toConfiguredPatternRule(configuredRegexes.get(index), className, fieldName, index));
		}
	}

	private PatternRule toConfiguredPatternRule(String regex, String className, String fieldName, int index) {
		String requiredRegex = requireNonEmpty("pattern", "regex", regex, className, fieldName, index);
		validateRegex("pattern", requiredRegex, className, fieldName, index);
		return new PatternRule(requiredRegex, null);
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

	private List<ExtensionRegexRule> toConfiguredExtensionRules(
		List<ValidationProperties.ExtensionRuleConstraint> configuredRules,
		String className,
		String fieldName
	) {
		if (configuredRules == null || configuredRules.isEmpty()) {
			return List.of();
		}

		List<ExtensionRegexRule> extensionRules = new ArrayList<>();
		for (int index = 0; index < configuredRules.size(); index++) {
			extensionRules.add(toConfiguredExtensionRule(configuredRules.get(index), className, fieldName, index));
		}
		return extensionRules;
	}

	private ExtensionRegexRule toConfiguredExtensionRule(
		ValidationProperties.ExtensionRuleConstraint configuredRule,
		String className,
		String fieldName,
		int index
	) {
		if (configuredRule == null) {
			throw new InvalidConstraintConfigurationException(
				"Invalid extensions constraint. rule must be non-null for class="
					+ className + ", field=" + fieldName + ", index=" + index);
		}

		String jsonPath = requireNonEmpty("extensions", "jsonPath", configuredRule.getJsonPath(), className, fieldName, index);
		validateJsonPath(jsonPath, className, fieldName, index);
		String regex = requireNonEmpty("extensions", "regex", configuredRule.getRegex(), className, fieldName, index);
		validateRegex("extensions", regex, className, fieldName, index);
		return new ExtensionRegexRule(jsonPath, regex);
	}

	private String requireNonEmpty(
		String constraintName,
		String propertyName,
		String value,
		String className,
		String fieldName,
		int index
	) {
		if (value == null || value.isEmpty()) {
			throw new InvalidConstraintConfigurationException(
				"Invalid " + constraintName + " constraint. " + propertyName + " must be non-empty for class="
					+ className + ", field=" + fieldName + ", index=" + index);
		}
		return value;
	}

	private void validateRegex(
		String constraintName,
		String regex,
		String className,
		String fieldName,
		int index
	) {
		try {
			Pattern.compile(regex);
		}
		catch (PatternSyntaxException exception) {
			throw new InvalidConstraintConfigurationException(
				"Invalid " + constraintName + " constraint. regex could not be compiled for class="
					+ className + ", field=" + fieldName + ", index=" + index + ", regex=" + regex,
				exception);
		}
	}

	private void validateJsonPath(String jsonPath, String className, String fieldName, int index) {
		try {
			JsonPath.compile(jsonPath);
		}
		catch (InvalidPathException | IllegalArgumentException exception) {
			throw new InvalidConstraintConfigurationException(
				"Invalid extensions constraint. jsonPath could not be compiled for class="
					+ className + ", field=" + fieldName + ", index=" + index + ", jsonPath=" + jsonPath,
				exception);
		}
	}
}
