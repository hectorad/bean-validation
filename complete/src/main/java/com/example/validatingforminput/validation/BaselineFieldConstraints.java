package com.example.validatingforminput.validation;

import java.util.ArrayList;
import java.util.List;

public record BaselineFieldConstraints(
	boolean notNull,
	boolean notBlank,
	Long min,
	Long max,
	Integer sizeMin,
	Integer sizeMax,
	List<PatternRule> patterns
) {

	public BaselineFieldConstraints {
		patterns = (patterns == null) ? List.of() : List.copyOf(patterns);
	}

	public static BaselineFieldConstraints empty() {
		return new BaselineFieldConstraints(false, false, null, null, null, null, new ArrayList<>());
	}
}
