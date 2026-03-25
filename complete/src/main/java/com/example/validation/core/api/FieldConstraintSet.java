package com.example.validation.core.api;

import java.util.List;

public record FieldConstraintSet(List<ValidationRule> rules) {

	public FieldConstraintSet {
		rules = (rules == null) ? List.of() : List.copyOf(rules);
	}

	public static FieldConstraintSet empty() {
		return new FieldConstraintSet(List.of());
	}
}
