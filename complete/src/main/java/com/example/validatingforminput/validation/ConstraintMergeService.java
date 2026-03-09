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

		boolean configNotNullEnabled = isTrue(effectiveConfig.getNotNull().getValue())
			|| isTrue(effectiveConfig.getNotNull().getHardValue());
		boolean configNotBlankEnabled = isTrue(effectiveConfig.getNotBlank().getValue())
			|| isTrue(effectiveConfig.getNotBlank().getHardValue());

		boolean notNull = baseline.notNull() || configNotNullEnabled;
		boolean notBlank = baseline.notBlank() || configNotBlankEnabled;
		String notNullMessage = configNotNullEnabled ? effectiveConfig.getNotNull().getMessage() : null;
		String notBlankMessage = configNotBlankEnabled ? effectiveConfig.getNotBlank().getMessage() : null;

		BoundCandidate minCandidate = strictestLowerBound(
			new BoundCandidate(baseline.min(), null),
			toInclusiveBound(effectiveConfig.getMin().getValue(), effectiveConfig.getMin().getMessage()),
			toInclusiveBound(effectiveConfig.getMin().getHardValue(), effectiveConfig.getMin().getMessage()),
			toDecimalBound(
				effectiveConfig.getDecimalMin().getValue(),
				effectiveConfig.getDecimalMin().getInclusive(),
				effectiveConfig.getDecimalMin().getMessage(),
				className,
				fieldName,
				"decimal-min.value",
				"decimal-min.inclusive"),
			toDecimalBound(
				effectiveConfig.getDecimalMin().getHardValue(),
				effectiveConfig.getDecimalMin().getHardInclusive(),
				effectiveConfig.getDecimalMin().getMessage(),
				className,
				fieldName,
				"decimal-min.hard-value",
				"decimal-min.hard-inclusive"));
		BoundCandidate maxCandidate = strictestUpperBound(
			new BoundCandidate(baseline.max(), null),
			toInclusiveBound(effectiveConfig.getMax().getValue(), effectiveConfig.getMax().getMessage()),
			toInclusiveBound(effectiveConfig.getMax().getHardValue(), effectiveConfig.getMax().getMessage()),
			toDecimalBound(
				effectiveConfig.getDecimalMax().getValue(),
				effectiveConfig.getDecimalMax().getInclusive(),
				effectiveConfig.getDecimalMax().getMessage(),
				className,
				fieldName,
				"decimal-max.value",
				"decimal-max.inclusive"),
			toDecimalBound(
				effectiveConfig.getDecimalMax().getHardValue(),
				effectiveConfig.getDecimalMax().getHardInclusive(),
				effectiveConfig.getDecimalMax().getMessage(),
				className,
				fieldName,
				"decimal-max.hard-value",
				"decimal-max.hard-inclusive"));

		NumericBound min = (minCandidate == null) ? null : minCandidate.bound();
		String minMessage = (minCandidate == null) ? null : minCandidate.message();
		NumericBound max = (maxCandidate == null) ? null : maxCandidate.bound();
		String maxMessage = (maxCandidate == null) ? null : maxCandidate.message();

		SizeBoundCandidate sizeMinCandidate = strictestComparableLowerBound(
			new SizeBoundCandidate(baseline.sizeMin(), null),
			toSizeBound(
				effectiveConfig.getSize().getMin().getValue(),
				effectiveConfig.getSize().getMin().getMessage(),
				className,
				fieldName,
				"size.min.value"),
			toSizeBound(
				effectiveConfig.getSize().getMin().getHardValue(),
				effectiveConfig.getSize().getMin().getMessage(),
				className,
				fieldName,
				"size.min.hardValue"));
		SizeBoundCandidate sizeMaxCandidate = strictestComparableUpperBound(
			new SizeBoundCandidate(baseline.sizeMax(), null),
			toSizeBound(
				effectiveConfig.getSize().getMax().getValue(),
				effectiveConfig.getSize().getMax().getMessage(),
				className,
				fieldName,
				"size.max.value"),
			toSizeBound(
				effectiveConfig.getSize().getMax().getHardValue(),
				effectiveConfig.getSize().getMax().getMessage(),
				className,
				fieldName,
				"size.max.hardValue"));

		Integer sizeMin = (sizeMinCandidate == null) ? null : sizeMinCandidate.value();
		Integer sizeMax = (sizeMaxCandidate == null) ? null : sizeMaxCandidate.value();
		String sizeMinMessage = (sizeMinCandidate == null) ? null : sizeMinCandidate.message();
		String sizeMaxMessage = (sizeMaxCandidate == null) ? null : sizeMaxCandidate.message();

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
			notNull,
			notNullMessage,
			notBlank,
			notBlankMessage,
			min,
			minMessage,
			max,
			maxMessage,
			sizeMin,
			sizeMinMessage,
			sizeMax,
			sizeMaxMessage,
			patterns,
			extensionRules);
	}

	private boolean isTrue(Boolean value) {
		return Boolean.TRUE.equals(value);
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
}
