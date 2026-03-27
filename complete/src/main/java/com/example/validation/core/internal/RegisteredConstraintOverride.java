package com.example.validation.core.internal;

import com.example.validation.core.spi.ConstraintOverrideSet;

record RegisteredConstraintOverride(String sourceId, ConstraintOverrideSet constraints) {

	RegisteredConstraintOverride {
		if (sourceId == null || sourceId.isBlank()) {
			throw new IllegalArgumentException("Registered validation override sourceId must not be blank.");
		}
		constraints = (constraints == null) ? new ConstraintOverrideSet() : constraints;
	}
}
