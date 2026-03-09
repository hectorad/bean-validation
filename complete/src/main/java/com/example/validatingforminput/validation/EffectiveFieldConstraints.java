package com.example.validatingforminput.validation;

import java.util.List;

public record EffectiveFieldConstraints(
	boolean notNull,
	boolean notBlank,
	NumericBound min,
	NumericBound max,
	Integer sizeMin,
	Integer sizeMax,
	List<PatternRule> patterns
) {

	public EffectiveFieldConstraints {
		patterns = (patterns == null) ? List.of() : List.copyOf(patterns);
	}
}
