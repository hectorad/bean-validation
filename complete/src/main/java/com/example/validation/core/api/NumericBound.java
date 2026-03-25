package com.example.validation.core.api;

import java.math.BigDecimal;

public record NumericBound(BigDecimal value, boolean inclusive) {

	public NumericBound {
		if (value == null) {
			throw new IllegalArgumentException("Numeric bound value must not be null.");
		}
		value = value.stripTrailingZeros();
	}

	public static NumericBound inclusive(long value) {
		return new NumericBound(BigDecimal.valueOf(value), true);
	}

	public static NumericBound stricterLower(NumericBound current, NumericBound candidate) {
		if (candidate == null) {
			return current;
		}
		if (current == null) {
			return candidate;
		}

		int comparison = candidate.value.compareTo(current.value);
		if (comparison > 0) {
			return candidate;
		}
		if (comparison < 0) {
			return current;
		}
		if (!candidate.inclusive && current.inclusive) {
			return candidate;
		}
		return current;
	}

	public static NumericBound stricterUpper(NumericBound current, NumericBound candidate) {
		if (candidate == null) {
			return current;
		}
		if (current == null) {
			return candidate;
		}

		int comparison = candidate.value.compareTo(current.value);
		if (comparison < 0) {
			return candidate;
		}
		if (comparison > 0) {
			return current;
		}
		if (!candidate.inclusive && current.inclusive) {
			return candidate;
		}
		return current;
	}
}
