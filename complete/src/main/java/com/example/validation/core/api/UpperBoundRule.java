package com.example.validation.core.api;

public record UpperBoundRule(NumericBound bound, String message) implements ValidationRule {

	public UpperBoundRule {
		if (bound == null) {
			throw new IllegalArgumentException("Upper bound rule requires a numeric bound.");
		}
	}
}
