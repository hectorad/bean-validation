package com.example.validatingforminput.validation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
public class ConstraintMergeService {

	public EffectiveFieldConstraints merge(
		BaselineFieldConstraints baseline,
		ValidationProperties.Constraints configuredConstraints,
		String className,
		String fieldName
	) {
		ValidationProperties.Constraints effectiveConfig = defaultConstraints(configuredConstraints);
		BooleanConstraintState notNull = resolveBooleanConstraint(baseline.notNull(), effectiveConfig.getNotNull());
		BooleanConstraintState notBlank = resolveBooleanConstraint(baseline.notBlank(), effectiveConfig.getNotBlank());
		BoundCandidate minCandidate = resolveLowerBound(
			baseline.min(),
			effectiveConfig.getMin(),
			effectiveConfig.getDecimalMin(),
			className,
			fieldName,
			"decimal-min");
		BoundCandidate maxCandidate = resolveUpperBound(
			baseline.max(),
			effectiveConfig.getMax(),
			effectiveConfig.getDecimalMax(),
			className,
			fieldName,
			"decimal-max");
		SizeBoundCandidate sizeMinCandidate = resolveSizeMin(
			baseline.sizeMin(),
			effectiveConfig.getSize().getMin(),
			className,
			fieldName);
		SizeBoundCandidate sizeMaxCandidate = resolveSizeMax(
			baseline.sizeMax(),
			effectiveConfig.getSize().getMax(),
			className,
			fieldName);

		NumericBound min = bound(minCandidate);
		NumericBound max = bound(maxCandidate);
		Integer sizeMin = sizeValue(sizeMinCandidate);
		Integer sizeMax = sizeValue(sizeMaxCandidate);

		validateNumericBounds(min, max, className, fieldName);
		if (sizeMin != null && sizeMax != null && sizeMin > sizeMax) {
			throw new InvalidConstraintConfigurationException(
				"Invalid size constraints. effectiveSizeMin > effectiveSizeMax for class="
					+ className + ", field=" + fieldName + ", effectiveSizeMin=" + sizeMin + ", effectiveSizeMax=" + sizeMax);
		}

		List<PatternRule> patterns = new ArrayList<>(baseline.patterns());
		appendConfiguredPatterns(
			patterns,
			effectiveConfig.getPattern().getRegexes(),
			effectiveConfig.getPattern().getMessage(),
			className,
			fieldName);
		List<ExtensionRegexRule> extensionRules =
			toConfiguredExtensionRules(effectiveConfig.getExtensions().getRules(), className, fieldName);

		return new EffectiveFieldConstraints(
			notNull.enabled(),
			notNull.message(),
			notBlank.enabled(),
			notBlank.message(),
			min,
			message(minCandidate),
			max,
			message(maxCandidate),
			sizeMin,
			message(sizeMinCandidate),
			sizeMax,
			message(sizeMaxCandidate),
			patterns,
			extensionRules);
	}

	private ValidationProperties.Constraints defaultConstraints(ValidationProperties.Constraints configuredConstraints) {
		return (configuredConstraints == null) ? new ValidationProperties.Constraints() : configuredConstraints;
	}

	private boolean isTrue(Boolean value) {
		return Boolean.TRUE.equals(value);
	}

	private BooleanConstraintState resolveBooleanConstraint(
		boolean baselineEnabled,
		ValidationProperties.ToggleConstraint configuredConstraint
	) {
		boolean configuredEnabled = isTrue(configuredConstraint.getValue()) || isTrue(configuredConstraint.getHardValue());
		return new BooleanConstraintState(
			baselineEnabled || configuredEnabled,
			configuredEnabled ? configuredConstraint.getMessage() : null);
	}

	private BoundCandidate resolveLowerBound(
		NumericBound baseline,
		ValidationProperties.NumericConstraint numericConstraint,
		ValidationProperties.DecimalConstraint decimalConstraint,
		String className,
		String fieldName,
		String decimalPropertyPrefix
	) {
		return strictestLowerBound(
			baselineCandidate(baseline),
			toInclusiveBound(numericConstraint.getValue(), numericConstraint.getMessage()),
			toInclusiveBound(numericConstraint.getHardValue(), numericConstraint.getMessage()),
			toDecimalBound(
				decimalConstraint.getValue(),
				decimalConstraint.getInclusive(),
				decimalConstraint.getMessage(),
				className,
				fieldName,
				decimalPropertyPrefix + ".value",
				decimalPropertyPrefix + ".inclusive"),
			toDecimalBound(
				decimalConstraint.getHardValue(),
				decimalConstraint.getHardInclusive(),
				decimalConstraint.getMessage(),
				className,
				fieldName,
				decimalPropertyPrefix + ".hard-value",
				decimalPropertyPrefix + ".hard-inclusive"));
	}

	private BoundCandidate resolveUpperBound(
		NumericBound baseline,
		ValidationProperties.NumericConstraint numericConstraint,
		ValidationProperties.DecimalConstraint decimalConstraint,
		String className,
		String fieldName,
		String decimalPropertyPrefix
	) {
		return strictestUpperBound(
			baselineCandidate(baseline),
			toInclusiveBound(numericConstraint.getValue(), numericConstraint.getMessage()),
			toInclusiveBound(numericConstraint.getHardValue(), numericConstraint.getMessage()),
			toDecimalBound(
				decimalConstraint.getValue(),
				decimalConstraint.getInclusive(),
				decimalConstraint.getMessage(),
				className,
				fieldName,
				decimalPropertyPrefix + ".value",
				decimalPropertyPrefix + ".inclusive"),
			toDecimalBound(
				decimalConstraint.getHardValue(),
				decimalConstraint.getHardInclusive(),
				decimalConstraint.getMessage(),
				className,
				fieldName,
				decimalPropertyPrefix + ".hard-value",
				decimalPropertyPrefix + ".hard-inclusive"));
	}

	private SizeBoundCandidate resolveSizeMin(
		Integer baseline,
		ValidationProperties.NumericConstraint configuredConstraint,
		String className,
		String fieldName
	) {
		return strictestComparableLowerBound(
			sizeCandidate(baseline),
			toSizeBound(
				configuredConstraint.getValue(),
				configuredConstraint.getMessage(),
				className,
				fieldName,
				"size.min.value"),
			toSizeBound(
				configuredConstraint.getHardValue(),
				configuredConstraint.getMessage(),
				className,
				fieldName,
				"size.min.hardValue"));
	}

	private SizeBoundCandidate resolveSizeMax(
		Integer baseline,
		ValidationProperties.NumericConstraint configuredConstraint,
		String className,
		String fieldName
	) {
		return strictestComparableUpperBound(
			sizeCandidate(baseline),
			toSizeBound(
				configuredConstraint.getValue(),
				configuredConstraint.getMessage(),
				className,
				fieldName,
				"size.max.value"),
			toSizeBound(
				configuredConstraint.getHardValue(),
				configuredConstraint.getMessage(),
				className,
				fieldName,
				"size.max.hardValue"));
	}

	private BoundCandidate strictestLowerBound(BoundCandidate... bounds) {
		BoundCandidate result = null;
		for (BoundCandidate bound : bounds) {
			result = stricterLower(result, bound);
		}
		return result;
	}

	private BoundCandidate strictestUpperBound(BoundCandidate... bounds) {
		BoundCandidate result = null;
		for (BoundCandidate bound : bounds) {
			result = stricterUpper(result, bound);
		}
		return result;
	}

	@SafeVarargs
	private final SizeBoundCandidate strictestComparableLowerBound(SizeBoundCandidate... bounds) {
		SizeBoundCandidate result = null;
		for (SizeBoundCandidate bound : bounds) {
			if (bound == null || bound.value() == null) {
				continue;
			}
			if (result == null || bound.value().compareTo(result.value()) > 0) {
				result = bound;
			}
		}
		return result;
	}

	@SafeVarargs
	private final SizeBoundCandidate strictestComparableUpperBound(SizeBoundCandidate... bounds) {
		SizeBoundCandidate result = null;
		for (SizeBoundCandidate bound : bounds) {
			if (bound == null || bound.value() == null) {
				continue;
			}
			if (result == null || bound.value().compareTo(result.value()) < 0) {
				result = bound;
			}
		}
		return result;
	}

	private BoundCandidate stricterLower(BoundCandidate current, BoundCandidate candidate) {
		if (candidate == null || candidate.bound() == null) {
			return current;
		}
		if (current == null || current.bound() == null) {
			return candidate;
		}

		NumericBound winner = NumericBound.stricterLower(current.bound(), candidate.bound());
		return (winner == candidate.bound()) ? candidate : current;
	}

	private BoundCandidate stricterUpper(BoundCandidate current, BoundCandidate candidate) {
		if (candidate == null || candidate.bound() == null) {
			return current;
		}
		if (current == null || current.bound() == null) {
			return candidate;
		}

		NumericBound winner = NumericBound.stricterUpper(current.bound(), candidate.bound());
		return (winner == candidate.bound()) ? candidate : current;
	}

	private BoundCandidate toInclusiveBound(Long value, String message) {
		return (value == null) ? null : new BoundCandidate(NumericBound.inclusive(value), message);
	}

	private BoundCandidate baselineCandidate(NumericBound bound) {
		return new BoundCandidate(bound, null);
	}

	private BoundCandidate toDecimalBound(
		BigDecimal value,
		Boolean inclusive,
		String message,
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
		return new BoundCandidate(new NumericBound(value, inclusive == null || inclusive), message);
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
		String configuredMessage,
		String className,
		String fieldName
	) {
		for (int index = 0; index < configuredRegexes.size(); index++) {
			patterns.add(toConfiguredPatternRule(configuredRegexes.get(index), configuredMessage, className, fieldName, index));
		}
	}

	private PatternRule toConfiguredPatternRule(
		String regex,
		String configuredMessage,
		String className,
		String fieldName,
		int index
	) {
		String requiredRegex = requireNonEmpty("pattern", "regex", regex, className, fieldName, index);
		validateRegex("pattern", requiredRegex, className, fieldName, index);
		return new PatternRule(requiredRegex, null, configuredMessage);
	}

	private SizeBoundCandidate toSizeBound(
		Long value,
		String message,
		String className,
		String fieldName,
		String propertyName
	) {
		Integer bound = toSizeInteger(value, className, fieldName, propertyName);
		return (bound == null) ? null : new SizeBoundCandidate(bound, message);
	}

	private SizeBoundCandidate sizeCandidate(Integer value) {
		return new SizeBoundCandidate(value, null);
	}

	private NumericBound bound(BoundCandidate candidate) {
		return (candidate == null) ? null : candidate.bound();
	}

	private Integer sizeValue(SizeBoundCandidate candidate) {
		return (candidate == null) ? null : candidate.value();
	}

	private String message(BoundCandidate candidate) {
		return (candidate == null) ? null : candidate.message();
	}

	private String message(SizeBoundCandidate candidate) {
		return (candidate == null) ? null : candidate.message();
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
		return new ExtensionRegexRule(jsonPath, regex, configuredRule.getMessage());
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

	private record BoundCandidate(NumericBound bound, String message) {
	}

	private record SizeBoundCandidate(Integer value, String message) {
	}

	private record BooleanConstraintState(boolean enabled, String message) {
	}
}
