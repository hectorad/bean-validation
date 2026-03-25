package com.example.validation.core.spi;

import com.example.validation.core.api.FieldConstraintSet;

public record ValidationFieldContext(
	String declaringClassName,
	String fieldName,
	Class<?> fieldType,
	FieldConstraintSet baselineConstraints
) {

	public ValidationFieldContext {
		baselineConstraints = (baselineConstraints == null) ? FieldConstraintSet.empty() : baselineConstraints;
	}
}
