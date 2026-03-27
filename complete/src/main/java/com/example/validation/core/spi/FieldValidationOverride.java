package com.example.validation.core.spi;

public record FieldValidationOverride(String fieldName, ConstraintOverrideSet constraints) {

	public FieldValidationOverride {
		fieldName = trimToNull(fieldName);
		constraints = (constraints == null) ? new ConstraintOverrideSet() : constraints;
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
