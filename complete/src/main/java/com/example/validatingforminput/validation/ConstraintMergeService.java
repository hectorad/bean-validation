package com.example.validatingforminput.validation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
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
		return merge(
			baseline,
			(configuredConstraints == null) ? List.of() : List.of(configuredConstraints),
			className,
			fieldName);
	}

	public EffectiveFieldConstraints merge(
		BaselineFieldConstraints baseline,
		Iterable<ValidationProperties.Constraints> configuredConstraints,
		String className,
		String fieldName
	) {
		BooleanConstraintState notNull = baselineBooleanState(baseline.notNull());
		BooleanConstraintState notBlank = baselineBooleanState(baseline.notBlank());
		BoundCandidate minCandidate = baselineCandidate(baseline.min());
		BoundCandidate maxCandidate = baselineCandidate(baseline.max());
		SizeBoundCandidate sizeMinCandidate = sizeCandidate(baseline.sizeMin());
		SizeBoundCandidate sizeMaxCandidate = sizeCandidate(baseline.sizeMax());

		List<PatternRule> patterns = new ArrayList<>(baseline.patterns());
		List<ExtensionRegexRule> extensionRules = new ArrayList<>();

		for (ValidationProperties.Constraints configuredConstraint : defaultConstraints(configuredConstraints)) {
			ValidationProperties.Constraints effectiveConfig =
				defaultIfNull(configuredConstraint, ValidationProperties.Constraints::new);
			notNull = resolveBooleanConstraint(notNull, effectiveConfig.getNotNull());
			notBlank = resolveBooleanConstraint(notBlank, effectiveConfig.getNotBlank());
			minCandidate = resolveBound(
				minCandidate,
				effectiveConfig.getMin(),
				effectiveConfig.getDecimalMin(),
				className,
				fieldName,
				"decimal-min",
				NumericBound::stricterLower);
			maxCandidate = resolveBound(
				maxCandidate,
				effectiveConfig.getMax(),
				effectiveConfig.getDecimalMax(),
				className,
				fieldName,
				"decimal-max",
				NumericBound::stricterUpper);
			ValidationProperties.SizeConstraint sizeConstraint =
				defaultIfNull(effectiveConfig.getSize(), ValidationProperties.SizeConstraint::new);
			sizeMinCandidate = resolveSizeBound(
				sizeMinCandidate,
				sizeConstraint.getMin(),
				className,
				fieldName,
				"size.min.value",
				"size.min.hardValue",
				true);
			sizeMaxCandidate = resolveSizeBound(
				sizeMaxCandidate,
				sizeConstraint.getMax(),
				className,
				fieldName,
				"size.max.value",
				"size.max.hardValue",
				false);

			ValidationProperties.PatternConstraint patternConstraint =
				defaultIfNull(effectiveConfig.getPattern(), ValidationProperties.PatternConstraint::new);
			appendConfiguredPatterns(
				patterns,
				patternConstraint.getRegexes(),
				patternConstraint.getFlags(),
				patternConstraint.getMessage(),
				className,
				fieldName);
			extensionRules.addAll(
				toConfiguredExtensionRules(
					defaultIfNull(effectiveConfig.getExtensions(), ValidationProperties.ExtensionsConstraint::new).getRules(),
					className,
					fieldName));
		}

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

	private Iterable<ValidationProperties.Constraints> defaultConstraints(
		Iterable<ValidationProperties.Constraints> configuredConstraints
	) {
		return (configuredConstraints == null) ? List.of() : configuredConstraints;
	}

	private <T> T defaultIfNull(T value, Supplier<T> factory) {
		return (value != null) ? value : factory.get();
	}

	private boolean isTrue(Boolean value) {
		return Boolean.TRUE.equals(value);
	}

	private BooleanConstraintState baselineBooleanState(boolean baselineEnabled) {
		return new BooleanConstraintState(baselineEnabled, null);
	}

	private BooleanConstraintState resolveBooleanConstraint(
		BooleanConstraintState current,
		ValidationProperties.ToggleConstraint configuredConstraint
	) {
		ValidationProperties.ToggleConstraint effectiveConstraint =
			defaultIfNull(configuredConstraint, ValidationProperties.ToggleConstraint::new);
		boolean configuredEnabled = isTrue(effectiveConstraint.getValue()) || isTrue(effectiveConstraint.getHardValue());
		if (!configuredEnabled) {
			return current;
		}
		if (!current.enabled() || current.message() == null) {
			return new BooleanConstraintState(true, effectiveConstraint.getMessage());
		}
		return current;
	}

	private BoundCandidate resolveBound(
		BoundCandidate current,
		ValidationProperties.NumericConstraint numericConstraint,
		ValidationProperties.DecimalConstraint decimalConstraint,
		String className,
		String fieldName,
		String decimalPropertyPrefix,
		BinaryOperator<NumericBound> numericSelector
	) {
		ValidationProperties.NumericConstraint effectiveNumericConstraint =
			defaultIfNull(numericConstraint, ValidationProperties.NumericConstraint::new);
		ValidationProperties.DecimalConstraint effectiveDecimalConstraint =
			defaultIfNull(decimalConstraint, ValidationProperties.DecimalConstraint::new);
		return strictestBound(
			numericSelector,
			current,
			toInclusiveBound(effectiveNumericConstraint.getValue(), effectiveNumericConstraint.getMessage()),
			toInclusiveBound(effectiveNumericConstraint.getHardValue(), effectiveNumericConstraint.getMessage()),
			toDecimalBound(
				effectiveDecimalConstraint.getValue(),
				effectiveDecimalConstraint.getInclusive(),
				effectiveDecimalConstraint.getMessage(),
				className,
				fieldName,
				decimalPropertyPrefix + ".value",
				decimalPropertyPrefix + ".inclusive"),
			toDecimalBound(
				effectiveDecimalConstraint.getHardValue(),
				effectiveDecimalConstraint.getHardInclusive(),
				effectiveDecimalConstraint.getMessage(),
				className,
				fieldName,
				decimalPropertyPrefix + ".hard-value",
				decimalPropertyPrefix + ".hard-inclusive"));
	}

	private SizeBoundCandidate resolveSizeBound(
		SizeBoundCandidate current,
		ValidationProperties.NumericConstraint configuredConstraint,
		String className,
		String fieldName,
		String valueProperty,
		String hardValueProperty,
		boolean selectStricterLower
	) {
		ValidationProperties.NumericConstraint effectiveConstraint =
			defaultIfNull(configuredConstraint, ValidationProperties.NumericConstraint::new);
		return strictestSizeBound(
			selectStricterLower,
			current,
			toSizeBound(
				effectiveConstraint.getValue(),
				effectiveConstraint.getMessage(),
				className,
				fieldName,
				valueProperty),
			toSizeBound(
				effectiveConstraint.getHardValue(),
				effectiveConstraint.getMessage(),
				className,
				fieldName,
				hardValueProperty));
	}

	private BoundCandidate strictestBound(BinaryOperator<NumericBound> numericSelector, BoundCandidate... bounds) {
		BoundCandidate result = null;
		for (BoundCandidate bound : bounds) {
			result = selectStricterBound(result, bound, numericSelector);
		}
		return result;
	}

	@SafeVarargs
	private final SizeBoundCandidate strictestSizeBound(boolean selectStricterLower, SizeBoundCandidate... bounds) {
		SizeBoundCandidate result = null;
		for (SizeBoundCandidate bound : bounds) {
			if (bound == null || bound.value() == null) {
				continue;
			}
			if (result == null
				|| (selectStricterLower ? bound.value().compareTo(result.value()) > 0 : bound.value().compareTo(result.value()) < 0)) {
				result = bound;
			}
		}
		return result;
	}

	private BoundCandidate selectStricterBound(
		BoundCandidate current,
		BoundCandidate candidate,
		BinaryOperator<NumericBound> numericSelector
	) {
		if (candidate == null || candidate.bound() == null) {
			return current;
		}
		if (current == null || current.bound() == null) {
			return candidate;
		}

		NumericBound winner = numericSelector.apply(current.bound(), candidate.bound());
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
		List<String> configuredFlags,
		String configuredMessage,
		String className,
		String fieldName
	) {
		List<String> effectiveRegexes = (configuredRegexes == null) ? List.of() : configuredRegexes;
		Set<jakarta.validation.constraints.Pattern.Flag> parsedFlags =
			parsePatternFlags((configuredFlags == null) ? List.of() : configuredFlags, className, fieldName);
		for (int index = 0; index < effectiveRegexes.size(); index++) {
			patterns.add(
				toConfiguredPatternRule(
					effectiveRegexes.get(index),
					parsedFlags,
					configuredMessage,
					className,
					fieldName,
					index));
		}
	}

	private PatternRule toConfiguredPatternRule(
		String regex,
		Set<jakarta.validation.constraints.Pattern.Flag> flags,
		String configuredMessage,
		String className,
		String fieldName,
		int index
	) {
		String requiredRegex = requireNonEmpty("pattern", "regex", regex, className, fieldName, index);
		validateRegex("pattern", requiredRegex, className, fieldName, index);
		return new PatternRule(requiredRegex, flags, configuredMessage);
	}

	private Set<jakarta.validation.constraints.Pattern.Flag> parsePatternFlags(
		List<String> flagNames,
		String className,
		String fieldName
	) {
		if (flagNames == null || flagNames.isEmpty()) {
			return EnumSet.noneOf(jakarta.validation.constraints.Pattern.Flag.class);
		}
		Set<jakarta.validation.constraints.Pattern.Flag> flags =
			EnumSet.noneOf(jakarta.validation.constraints.Pattern.Flag.class);
		for (String flagName : flagNames) {
			try {
				flags.add(jakarta.validation.constraints.Pattern.Flag.valueOf(flagName));
			}
			catch (IllegalArgumentException exception) {
				throw new InvalidConstraintConfigurationException(
					"Invalid pattern flag '" + flagName + "' for class=" + className
						+ ", field=" + fieldName + ". Valid flags: "
						+ java.util.Arrays.toString(jakarta.validation.constraints.Pattern.Flag.values()),
					exception);
			}
		}
		return flags;
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
