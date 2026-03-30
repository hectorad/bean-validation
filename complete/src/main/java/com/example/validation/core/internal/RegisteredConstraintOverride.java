package com.example.validation.core.internal;

import com.example.validation.core.spi.ConstraintOverrideSet;

import java.util.List;

record RegisteredConstraintOverride(String sourceId, ConstraintOverrideSet constraints) {

	RegisteredConstraintOverride {
		if (sourceId == null || sourceId.isBlank()) {
			throw new IllegalArgumentException("Registered validation override sourceId must not be blank.");
		}
		constraints = (constraints == null) ? new ConstraintOverrideSet() : constraints;
	}

	static String renderSources(List<RegisteredConstraintOverride> contributions) {
		return contributions.stream()
			.map(RegisteredConstraintOverride::sourceId)
			.distinct()
			.toList()
			.toString();
	}
}
