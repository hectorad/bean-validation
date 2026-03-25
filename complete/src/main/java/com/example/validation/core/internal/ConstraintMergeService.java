package com.example.validation.core.internal;

import com.example.validation.core.api.FieldConstraintSet;
import com.example.validation.core.api.JsonPathRegexRule;
import com.example.validation.core.api.LowerBoundRule;
import com.example.validation.core.api.NotBlankRule;
import com.example.validation.core.api.NotNullRule;
import com.example.validation.core.api.NumericBound;
import com.example.validation.core.api.PatternRule;
import com.example.validation.core.api.SizeRule;
import com.example.validation.core.api.UpperBoundRule;
import com.example.validation.core.api.ValidationRule;
import com.example.validation.core.spi.ConstraintContribution;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ConstraintMergeService {

	public EffectiveFieldConstraints merge(
		BaselineFieldConstraints baseline,
		ValidationProperties.Constraints configuredConstraints,
		String className,
		String fieldName
	) {
		if (configuredConstraints == null) {
			return merge(baseline, List.of(), className, fieldName);
		}
		return merge(
			baseline,
			List.of(PropertiesFieldConstraintContributor.fromConstraints("properties", configuredConstraints, className, fieldName)),
			className,
			fieldName);
	}

	public EffectiveFieldConstraints merge(
		BaselineFieldConstraints baseline,
		Iterable<ConstraintContribution> contributions,
		String className,
		String fieldName
	) {
		boolean notNullEnabled = baseline.notNull();
		String notNullMessage = null;
		boolean notBlankEnabled = baseline.notBlank();
		String notBlankMessage = null;
		BoundCandidate minCandidate = baselineCandidate(baseline.min());
		BoundCandidate maxCandidate = baselineCandidate(baseline.max());
		SizeBoundCandidate sizeMinCandidate = sizeCandidate(baseline.sizeMin());
		SizeBoundCandidate sizeMaxCandidate = sizeCandidate(baseline.sizeMax());
		List<PatternRule> patterns = new ArrayList<>(baseline.patterns());
		List<JsonPathRegexRule> extensionRules = new ArrayList<>(baseline.extensionRules());

		for (ConstraintContribution contribution : safeContributions(contributions)) {
			FieldConstraintSet constraintSet = defaultConstraintSet(contribution);
			for (ValidationRule rule : constraintSet.rules()) {
				switch (rule) {
					case NotNullRule notNullRule -> {
						notNullEnabled = true;
						notNullMessage = chooseBooleanMessage(notNullMessage, notNullRule.message());
					}
					case NotBlankRule notBlankRule -> {
						notBlankEnabled = true;
						notBlankMessage = chooseBooleanMessage(notBlankMessage, notBlankRule.message());
					}
					case LowerBoundRule lowerBoundRule -> minCandidate =
						selectStricterBound(minCandidate, new BoundCandidate(lowerBoundRule.bound(), lowerBoundRule.message()), NumericBound::stricterLower);
					case UpperBoundRule upperBoundRule -> maxCandidate =
						selectStricterBound(maxCandidate, new BoundCandidate(upperBoundRule.bound(), upperBoundRule.message()), NumericBound::stricterUpper);
					case SizeRule sizeRule -> {
						sizeMinCandidate = selectStricterSizeBound(
							true,
							sizeMinCandidate,
							toSizeCandidate(sizeRule.min(), sizeRule.minMessage(), className, fieldName, "size.min"));
						sizeMaxCandidate = selectStricterSizeBound(
							false,
							sizeMaxCandidate,
							toSizeCandidate(sizeRule.max(), sizeRule.maxMessage(), className, fieldName, "size.max"));
					}
					case PatternRule patternRule -> appendPattern(patterns, patternRule, className, fieldName);
					case JsonPathRegexRule jsonPathRegexRule -> extensionRules.add(validateExtensionRule(jsonPathRegexRule, className, fieldName));
				}
			}
		}

		NumericBound min = bound(minCandidate);
		NumericBound max = bound(maxCandidate);
		Integer sizeMin = sizeValue(sizeMinCandidate);
		Integer sizeMax = sizeValue(sizeMaxCandidate);

		validateNumericBounds(min, max, className, fieldName);
		validateSizeBounds(sizeMin, sizeMax, className, fieldName);

		return new EffectiveFieldConstraints(
			notNullEnabled,
			notNullMessage,
			notBlankEnabled,
			notBlankMessage,
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

	private List<ConstraintContribution> safeContributions(Iterable<ConstraintContribution> contributions) {
		if (contributions == null) {
			return List.of();
		}
		List<ConstraintContribution> safeContributions = new ArrayList<>();
		for (ConstraintContribution contribution : contributions) {
			if (contribution != null) {
				safeContributions.add(contribution);
			}
		}
		return safeContributions;
	}

	private FieldConstraintSet defaultConstraintSet(ConstraintContribution contribution) {
		return (contribution == null || contribution.constraints() == null)
			? FieldConstraintSet.empty()
			: contribution.constraints();
	}

	private String chooseBooleanMessage(String current, String candidate) {
		return (current == null && candidate != null) ? candidate : current;
	}

	private BoundCandidate selectStricterBound(
		BoundCandidate current,
		BoundCandidate candidate,
		java.util.function.BinaryOperator<NumericBound> selector
	) {
		if (candidate == null || candidate.bound() == null) {
			return current;
		}
		if (current == null || current.bound() == null) {
			return candidate;
		}
		NumericBound winner = selector.apply(current.bound(), candidate.bound());
		return (winner == candidate.bound()) ? candidate : current;
	}

	private SizeBoundCandidate selectStricterSizeBound(boolean lowerBound, SizeBoundCandidate current, SizeBoundCandidate candidate) {
		if (candidate == null || candidate.value() == null) {
			return current;
		}
		if (current == null || current.value() == null) {
			return candidate;
		}
		int comparison = candidate.value().compareTo(current.value());
		if ((lowerBound && comparison > 0) || (!lowerBound && comparison < 0)) {
			return candidate;
		}
		return current;
	}

	private void appendPattern(List<PatternRule> patterns, PatternRule patternRule, String className, String fieldName) {
		PatternRule validatedPattern = validatePatternRule(patternRule, className, fieldName);
		Map<PatternIdentity, Integer> indexes = indexPatterns(patterns);
		PatternIdentity identity = new PatternIdentity(validatedPattern.regex());
		Integer existingIndex = indexes.get(identity);
		if (existingIndex == null) {
			patterns.add(validatedPattern);
			return;
		}
		PatternRule existing = patterns.get(existingIndex);
		if (validatedPattern.message() != null && !Objects.equals(existing.message(), validatedPattern.message())) {
			patterns.set(existingIndex, new PatternRule(existing.regex(), validatedPattern.message()));
		}
	}

	private Map<PatternIdentity, Integer> indexPatterns(List<PatternRule> patterns) {
		Map<PatternIdentity, Integer> indexes = new LinkedHashMap<>();
		for (int index = 0; index < patterns.size(); index++) {
			indexes.putIfAbsent(new PatternIdentity(patterns.get(index).regex()), index);
		}
		return indexes;
	}

	private PatternRule validatePatternRule(PatternRule patternRule, String className, String fieldName) {
		if (patternRule == null || patternRule.regex() == null || patternRule.regex().isBlank()) {
			throw invalid("pattern.regex must be non-empty", className, fieldName);
		}
		validateRegex("pattern", patternRule.regex(), className, fieldName);
		return patternRule;
	}

	private JsonPathRegexRule validateExtensionRule(JsonPathRegexRule rule, String className, String fieldName) {
		if (rule == null || rule.jsonPath() == null || rule.jsonPath().isBlank()) {
			throw invalid("extensions.jsonPath must be non-empty", className, fieldName);
		}
		if (rule.regex() == null || rule.regex().isBlank()) {
			throw invalid("extensions.regex must be non-empty", className, fieldName);
		}
		validateJsonPath(rule.jsonPath(), className, fieldName);
		validateRegex("extensions", rule.regex(), className, fieldName);
		return rule;
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

	private SizeBoundCandidate toSizeCandidate(Integer value, String message, String className, String fieldName, String propertyName) {
		if (value == null) {
			return null;
		}
		if (value < 0) {
			throw invalid(propertyName + " must be >= 0", className, fieldName);
		}
		return new SizeBoundCandidate(value, message);
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

	private record BoundCandidate(NumericBound bound, String message) {
	}

	private record SizeBoundCandidate(Integer value, String message) {
	}

	private record PatternIdentity(String regex) {
	}
}
