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

import java.util.ArrayList;
import java.util.List;

public record BaselineFieldConstraints(
	boolean notNull,
	boolean notBlank,
	NumericBound min,
	NumericBound max,
	Integer sizeMin,
	Integer sizeMax,
	List<PatternRule> patterns,
	List<JsonPathRegexRule> extensionRules
) {

	public BaselineFieldConstraints {
		patterns = (patterns == null) ? List.of() : List.copyOf(patterns);
		extensionRules = (extensionRules == null) ? List.of() : List.copyOf(extensionRules);
	}

	public BaselineFieldConstraints(
		boolean notNull,
		boolean notBlank,
		NumericBound min,
		NumericBound max,
		Integer sizeMin,
		Integer sizeMax,
		List<PatternRule> patterns
	) {
		this(notNull, notBlank, min, max, sizeMin, sizeMax, patterns, List.of());
	}

	public static BaselineFieldConstraints empty() {
		return new BaselineFieldConstraints(false, false, null, null, null, null, List.of(), List.of());
	}

	FieldConstraintSet toConstraintSet() {
		List<ValidationRule> rules = new ArrayList<>();
		if (notNull) {
			rules.add(new NotNullRule(null));
		}
		if (notBlank) {
			rules.add(new NotBlankRule(null));
		}
		if (min != null) {
			rules.add(new LowerBoundRule(min, null));
		}
		if (max != null) {
			rules.add(new UpperBoundRule(max, null));
		}
		if (sizeMin != null || sizeMax != null) {
			rules.add(new SizeRule(sizeMin, sizeMax, null, null));
		}
		rules.addAll(patterns);
		rules.addAll(extensionRules);
		return new FieldConstraintSet(rules);
	}
}
