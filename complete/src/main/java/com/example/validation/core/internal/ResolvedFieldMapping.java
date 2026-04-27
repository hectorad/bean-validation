package com.example.validation.core.internal;

public record ResolvedFieldMapping(
	String fieldName,
	Class<?> declaringClass,
	Class<?> fieldType,
	BaselineFieldConstraints baselineConstraints,
	FieldValidationMetadata validationMetadata
) {

	public ResolvedFieldMapping {
		validationMetadata = (validationMetadata == null) ? FieldValidationMetadata.empty() : validationMetadata;
	}
}
