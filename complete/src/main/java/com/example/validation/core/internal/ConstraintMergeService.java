package com.example.validation.core.internal;

import com.example.validation.core.api.JsonPathRegexRule;
import com.example.validation.core.api.NumericBound;
import com.example.validation.core.api.PatternRule;
import com.example.validation.core.spi.ConstraintOverrideSet;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ConstraintMergeService {

	public EffectiveFieldConstraints merge(
		BaselineFieldConstraints baseline,
		ConstraintOverrideSet configuredConstraints,
		String className,
		String fieldName
	) {
		return merge(
			baseline,
			(configuredConstraints == null)
				? List.<RegisteredConstraintOverride>of()
				: List.of(new RegisteredConstraintOverride("configured", configuredConstraints)),
			className,
			fieldName);
	}

	public EffectiveFieldConstraints merge(
		BaselineFieldConstraints baseline,
		ValidationProperties.Constraints configuredConstraints,
		String className,
		String fieldName
	) {
		return merge(baseline, ValidationProperties.toConstraintOverrideSet(configuredConstraints), className, fieldName);
	}

	public EffectiveFieldConstraints merge(
		BaselineFieldConstraints baseline,
		Iterable<RegisteredConstraintOverride> contributions,
		String className,
		String fieldName
	) {
		List<ConstraintOverrideSet> configuredConstraints = defaultConstraints(contributions);
		BooleanConstraintState notNull = resolveBooleanConstraint(
			baseline.notNull(),
			configuredConstraints,
			ConstraintOverrideSet::getNotNull);
		BooleanConstraintState notBlank = resolveBooleanConstraint(
			baseline.notBlank(),
			configuredConstraints,
			ConstraintOverrideSet::getNotBlank);
		BoundCandidate minCandidate = resolveBound(
			baseline.min(),
			configuredConstraints,
			ConstraintOverrideSet::getMin,
			ConstraintOverrideSet::getDecimalMin,
			className,
			fieldName,
			"decimal-min",
			NumericBound::stricterLower);
		BoundCandidate maxCandidate = resolveBound(
			baseline.max(),
			configuredConstraints,
			ConstraintOverrideSet::getMax,
			ConstraintOverrideSet::getDecimalMax,
			className,
			fieldName,
			"decimal-max",
			NumericBound::stricterUpper);
		SizeBoundCandidate sizeMinCandidate = resolveSizeBound(
			baseline.sizeMin(),
			configuredConstraints,
			constraint -> constraint.getSize().getMin(),
			className,
			fieldName,
			"size.min.value",
			true);
		SizeBoundCandidate sizeMaxCandidate = resolveSizeBound(
			baseline.sizeMax(),
			configuredConstraints,
			constraint -> constraint.getSize().getMax(),
			className,
			fieldName,
			"size.max.value",
			false);

		NumericBound min = bound(minCandidate);
		NumericBound max = bound(maxCandidate);
		Integer sizeMin = sizeValue(sizeMinCandidate);
		Integer sizeMax = sizeValue(sizeMaxCandidate);

		validateNumericBounds(min, max, className, fieldName);
		validateSizeBounds(sizeMin, sizeMax, className, fieldName);

		List<PatternRule> patterns = new ArrayList<>(baseline.patterns());
		List<JsonPathRegexRule> extensionRules = new ArrayList<>(baseline.extensionRules());
		for (ConstraintOverrideSet configuredConstraint : configuredConstraints) {
			appendConfiguredPatterns(
				patterns,
				configuredConstraint.getPattern().getRegexes(),
				configuredConstraint.getPattern().getMessage(),
				className,
				fieldName);
			extensionRules.addAll(
				toConfiguredExtensionRules(configuredConstraint.getExtensions().getRules(), className, fieldName));
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

	private List<ConstraintOverrideSet> defaultConstraints(Iterable<RegisteredConstraintOverride> contributions) {
		if (contributions == null) {
			return List.of();
		}
		List<ConstraintOverrideSet> safeContributions = new ArrayList<>();
		for (RegisteredConstraintOverride contribution : contributions) {
			safeContributions.add(contribution == null ? new ConstraintOverrideSet() : contribution.constraints());
		}
		return safeContributions;
	}

	private boolean isTrue(Boolean value) {
		return Boolean.TRUE.equals(value);
	}

	private BooleanConstraintState resolveBooleanConstraint(
		boolean baselineEnabled,
		List<ConstraintOverrideSet> configuredConstraints,
		Function<ConstraintOverrideSet, ConstraintOverrideSet.ToggleConstraint> selector
	) {
		if (baselineEnabled) {
			return new BooleanConstraintState(true, resolveBaselineBooleanMessage(configuredConstraints, selector));
		}
		for (ConstraintOverrideSet configuredConstraint : configuredConstraints) {
			ConstraintOverrideSet.ToggleConstraint candidate = selector.apply(configuredConstraint);
			if (isTrue(candidate.getValue())) {
				return new BooleanConstraintState(true, candidate.getMessage());
			}
		}
		return new BooleanConstraintState(false, null);
	}

	private String resolveBaselineBooleanMessage(
		List<ConstraintOverrideSet> configuredConstraints,
		Function<ConstraintOverrideSet, ConstraintOverrideSet.ToggleConstraint> selector
	) {
		for (ConstraintOverrideSet configuredConstraint : configuredConstraints) {
			ConstraintOverrideSet.ToggleConstraint candidate = selector.apply(configuredConstraint);
			if (candidate.getMessage() != null && candidate.getValue() != Boolean.FALSE) {
				return candidate.getMessage();
			}
		}
		return null;
	}

	private BoundCandidate resolveBound(
		NumericBound baseline,
		List<ConstraintOverrideSet> configuredConstraints,
		Function<ConstraintOverrideSet, ConstraintOverrideSet.NumericConstraint> numericAccessor,
		Function<ConstraintOverrideSet, ConstraintOverrideSet.DecimalConstraint> decimalAccessor,
		String className,
		String fieldName,
		String decimalPropertyPrefix,
		BinaryOperator<NumericBound> numericSelector
	) {
		BoundCandidate result = baselineCandidate(baseline);
		for (ConstraintOverrideSet configuredConstraint : configuredConstraints) {
			ConstraintOverrideSet.NumericConstraint numericConstraint = numericAccessor.apply(configuredConstraint);
			result = selectStricterBound(
				result,
				toInclusiveBound(numericConstraint.getValue(), numericConstraint.getMessage()),
				numericSelector);

			ConstraintOverrideSet.DecimalConstraint decimalConstraint = decimalAccessor.apply(configuredConstraint);
			result = selectStricterBound(
				result,
				toDecimalBound(
					decimalConstraint.getValue(),
					decimalConstraint.getInclusive(),
					decimalConstraint.getMessage(),
					className,
					fieldName,
					decimalPropertyPrefix + ".value",
					decimalPropertyPrefix + ".inclusive"),
				numericSelector);
		}
		return result;
	}

	private SizeBoundCandidate resolveSizeBound(
		Integer baseline,
		List<ConstraintOverrideSet> configuredConstraints,
		Function<ConstraintOverrideSet, ConstraintOverrideSet.NumericConstraint> selector,
		String className,
		String fieldName,
		String valueProperty,
		boolean selectStricterLower
	) {
		SizeBoundCandidate result = sizeCandidate(baseline);
		for (ConstraintOverrideSet configuredConstraint : configuredConstraints) {
			ConstraintOverrideSet.NumericConstraint candidate = selector.apply(configuredConstraint);
			SizeBoundCandidate candidateBound = toSizeBound(
				candidate.getValue(),
				candidate.getMessage(),
				className,
				fieldName,
				valueProperty);
			if (candidateBound == null || candidateBound.value() == null) {
				continue;
			}
			if (result == null
				|| result.value() == null
				|| (selectStricterLower
					? candidateBound.value().compareTo(result.value()) > 0
					: candidateBound.value().compareTo(result.value()) < 0)) {
				result = candidateBound;
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

	private SizeBoundCandidate toSizeBound(
		Long value,
		String message,
		String className,
		String fieldName,
		String propertyName
	) {
		if (value == null) {
			return null;
		}
		if (value < 0) {
			throw invalid(propertyName + " must be >= 0", className, fieldName);
		}
		try {
			return new SizeBoundCandidate(Math.toIntExact(value), message);
		}
		catch (ArithmeticException exception) {
			throw new InvalidConstraintConfigurationException(
				"Invalid size constraint. " + propertyName + " exceeds Integer.MAX_VALUE for class="
					+ className + ", field=" + fieldName + ", value=" + value,
				exception);
		}
	}

	private void appendConfiguredPatterns(
		List<PatternRule> patterns,
		List<String> configuredRegexes,
		String configuredMessage,
		String className,
		String fieldName
	) {
		Map<PatternIdentity, Integer> patternIndexes = indexPatterns(patterns);
		for (int index = 0; index < configuredRegexes.size(); index++) {
			PatternRule configuredPattern = toConfiguredPatternRule(
				configuredRegexes.get(index),
				configuredMessage,
				className,
				fieldName,
				index);
			PatternIdentity identity = new PatternIdentity(configuredPattern.regex());
			Integer existingIndex = patternIndexes.get(identity);
			if (existingIndex == null) {
				patterns.add(configuredPattern);
				patternIndexes.put(identity, patterns.size() - 1);
				continue;
			}
			PatternRule existing = patterns.get(existingIndex);
			if (configuredPattern.message() != null && !Objects.equals(existing.message(), configuredPattern.message())) {
				patterns.set(existingIndex, new PatternRule(existing.regex(), configuredPattern.message()));
			}
		}
	}

	private PatternRule toConfiguredPatternRule(
		String configuredRegex,
		String configuredMessage,
		String className,
		String fieldName,
		int index
	) {
		if (configuredRegex == null || configuredRegex.isBlank()) {
			throw invalid("regex must be non-empty", className, fieldName);
		}
		validateRegex("pattern", configuredRegex, className, fieldName);
		return new PatternRule(configuredRegex.trim(), configuredMessage);
	}

	private List<JsonPathRegexRule> toConfiguredExtensionRules(
		List<ConstraintOverrideSet.ExtensionRule> configuredRules,
		String className,
		String fieldName
	) {
		if (configuredRules == null || configuredRules.isEmpty()) {
			return List.of();
		}
		List<JsonPathRegexRule> converted = new ArrayList<>();
		for (int index = 0; index < configuredRules.size(); index++) {
			ConstraintOverrideSet.ExtensionRule configuredRule = configuredRules.get(index);
			if (configuredRule == null) {
				throw invalid("extensions.rules[" + index + "] must not be null", className, fieldName);
			}
			String jsonPath = configuredRule.getJsonPath();
			String regex = configuredRule.getRegex();
			if (jsonPath == null || jsonPath.isBlank()) {
				throw invalid("extensions.rules[" + index + "].jsonPath must be non-empty", className, fieldName);
			}
			if (regex == null || regex.isBlank()) {
				throw invalid("extensions.rules[" + index + "].regex must be non-empty", className, fieldName);
			}
			validateJsonPath(jsonPath, className, fieldName);
			validateRegex("extensions", regex, className, fieldName);
			converted.add(new JsonPathRegexRule(jsonPath.trim(), regex.trim(), configuredRule.getMessage()));
		}
		return converted;
	}

	private Map<PatternIdentity, Integer> indexPatterns(List<PatternRule> patterns) {
		Map<PatternIdentity, Integer> indexes = new LinkedHashMap<>();
		for (int index = 0; index < patterns.size(); index++) {
			indexes.putIfAbsent(new PatternIdentity(patterns.get(index).regex()), index);
		}
		return indexes;
	}

	private void validateRegex(String constraintName, String regex, String className, String fieldName) {
		try {
			Pattern.compile(regex);
		}
		catch (PatternSyntaxException exception) {
			throw new InvalidConstraintConfigurationException(
				"Invalid " + constraintName + " constraint. regex could not be compiled for class="
					+ className + ", field=" + fieldName + ", regex=" + regex,
				exception);
		}
	}

	private void validateJsonPath(String jsonPath, String className, String fieldName) {
		try {
			JsonPath.compile(jsonPath);
		}
		catch (InvalidPathException | IllegalArgumentException exception) {
			throw new InvalidConstraintConfigurationException(
				"Invalid extensions constraint. jsonPath could not be compiled for class="
					+ className + ", field=" + fieldName + ", jsonPath=" + jsonPath,
				exception);
		}
	}

	private void validateNumericBounds(NumericBound min, NumericBound max, String className, String fieldName) {
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

	private void validateSizeBounds(Integer sizeMin, Integer sizeMax, String className, String fieldName) {
		if (sizeMin != null && sizeMax != null && sizeMin > sizeMax) {
			throw new InvalidConstraintConfigurationException(
				"Invalid size constraints. effectiveSizeMin > effectiveSizeMax for class="
					+ className + ", field=" + fieldName + ", effectiveSizeMin=" + sizeMin + ", effectiveSizeMax=" + sizeMax);
		}
	}

	private BoundCandidate baselineCandidate(NumericBound bound) {
		return new BoundCandidate(bound, null);
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

	private String render(NumericBound bound) {
		return bound.value().toPlainString() + " (inclusive=" + bound.inclusive() + ")";
	}

	private InvalidConstraintConfigurationException invalid(String message, String className, String fieldName) {
		return new InvalidConstraintConfigurationException(
			"Invalid configured constraints for class=" + className + ", field=" + fieldName + ". " + message);
	}

	private record BooleanConstraintState(boolean enabled, String message) {
	}

	private record BoundCandidate(NumericBound bound, String message) {
	}

	private record SizeBoundCandidate(Integer value, String message) {
	}

	private record PatternIdentity(String regex) {
	}
}
