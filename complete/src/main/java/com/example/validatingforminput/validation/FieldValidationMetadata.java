package com.example.validatingforminput.validation;

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

	private static <T> List<T> copyOf(List<T> values) {
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
		path = copyOf(path);
		constraintAnnotations = copyOf(constraintAnnotations);
		groupConversions = copyOf(groupConversions);
	}

	boolean isEmpty() {
		return constraintAnnotations.isEmpty() && !cascaded && groupConversions.isEmpty();
	}

	private static <T> List<T> copyOf(List<T> values) {
		return (values == null) ? List.of() : List.copyOf(values);
	}
}
