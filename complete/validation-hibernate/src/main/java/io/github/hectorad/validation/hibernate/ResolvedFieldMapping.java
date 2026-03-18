package io.github.hectorad.validation.hibernate;

import io.github.hectorad.validation.BaselineFieldConstraints;

public record ResolvedFieldMapping(
	String fieldName,
	BaselineFieldConstraints baselineConstraints,
	FieldValidationMetadata validationMetadata
) {

	public ResolvedFieldMapping {
		validationMetadata = (validationMetadata == null) ? FieldValidationMetadata.empty() : validationMetadata;
	}
}
