package com.example.validation.core.internal;

import java.lang.annotation.Annotation;
import java.util.List;

record FieldValidationMetadata(
	List<Annotation> constraintAnnotations,
	boolean cascaded,
	List<GroupConversionMapping> groupConversions,
	List<ContainerElementValidationMetadata> containerElements
) {

	FieldValidationMetadata {
		constraintAnnotations = copyOf(constraintAnnotations);
		groupConversions = copyOf(groupConversions);
		containerElements = copyOf(containerElements);
	}

	static FieldValidationMetadata empty() {
		return new FieldValidationMetadata(List.of(), false, List.of(), List.of());
	}

	boolean isEmpty() {
		return constraintAnnotations.isEmpty()
			&& !cascaded
			&& groupConversions.isEmpty()
			&& containerElements.isEmpty();
	}

	static <T> List<T> copyOf(List<T> values) {
		return (values == null) ? List.of() : List.copyOf(values);
	}
}

record GroupConversionMapping(Class<?> from, Class<?> to) {
}

record ContainerElementValidationMetadata(
	List<Integer> path,
	List<Annotation> constraintAnnotations,
	boolean cascaded,
	List<GroupConversionMapping> groupConversions
) {

	ContainerElementValidationMetadata {
		path = FieldValidationMetadata.copyOf(path);
		constraintAnnotations = FieldValidationMetadata.copyOf(constraintAnnotations);
		groupConversions = FieldValidationMetadata.copyOf(groupConversions);
	}

	boolean isEmpty() {
		return constraintAnnotations.isEmpty() && !cascaded && groupConversions.isEmpty();
	}
}
