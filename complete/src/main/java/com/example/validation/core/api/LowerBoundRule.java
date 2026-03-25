package com.example.validation.core.api;

public record LowerBoundRule(NumericBound bound, String message) implements ValidationRule {

	public LowerBoundRule {
		if (bound == null) {
			throw new IllegalArgumentException("Lower bound rule requires a numeric bound.");
		}
	}
}
