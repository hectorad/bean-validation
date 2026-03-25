package com.example.validation.core.spi;

import com.example.validation.core.api.FieldConstraintSet;

public record ConstraintContribution(String sourceId, FieldConstraintSet constraints) {

	public ConstraintContribution {
		if (sourceId == null || sourceId.isBlank()) {
			throw new IllegalArgumentException("Constraint contribution sourceId must not be blank.");
		}
		constraints = (constraints == null) ? FieldConstraintSet.empty() : constraints;
	}
}
