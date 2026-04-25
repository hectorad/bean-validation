package com.example.validation.core.internal;

import java.lang.reflect.Field;

public record ResolvedFieldMapping(
	String fieldName,
	Class<?> fieldType,
	Field field,
	Class<?> declaringClass,
	BaselineFieldConstraints baselineConstraints,
	FieldValidationMetadata validationMetadata
) {

	public ResolvedFieldMapping(
		String fieldName,
		Class<?> fieldType,
		BaselineFieldConstraints baselineConstraints,
		FieldValidationMetadata validationMetadata
	) {
		this(fieldName, fieldType, null, null, baselineConstraints, validationMetadata);
	}

	public ResolvedFieldMapping {
		if (field != null) {
			fieldType = field.getType();
			declaringClass = field.getDeclaringClass();
		}
		validationMetadata = (validationMetadata == null) ? FieldValidationMetadata.empty() : validationMetadata;
	}

	public boolean isInheritedFrom(Class<?> mappedClass) {
		return declaringClass != null && !declaringClass.equals(mappedClass);
	}
}
