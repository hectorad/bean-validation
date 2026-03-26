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
		return selectStricter(current, candidate, 1);
	}

	public static NumericBound stricterUpper(NumericBound current, NumericBound candidate) {
		return selectStricter(current, candidate, -1);
	}

	private static NumericBound selectStricter(NumericBound current, NumericBound candidate, int stricterDirection) {
		if (candidate == null) {
			return current;
		}
		if (current == null) {
			return candidate;
		}
		int comparison = candidate.value.compareTo(current.value);
		if (comparison == stricterDirection) {
			return candidate;
		}
		if (comparison == -stricterDirection) {
			return current;
		}
		if (!candidate.inclusive && current.inclusive) {
			return candidate;
		}
		return current;
	}
}
