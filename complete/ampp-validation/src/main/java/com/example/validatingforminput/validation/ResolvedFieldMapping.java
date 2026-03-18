package com.example.validatingforminput.validation;

public record ResolvedFieldMapping(
	String fieldName,
	BaselineFieldConstraints baselineConstraints,
	FieldValidationMetadata validationMetadata
) {

	public ResolvedFieldMapping {
		validationMetadata = (validationMetadata == null) ? FieldValidationMetadata.empty() : validationMetadata;
	}
}
