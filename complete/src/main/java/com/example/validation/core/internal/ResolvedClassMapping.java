package com.example.validation.core.internal;

import java.util.List;

public record ResolvedClassMapping(
	String className,
	Class<?> clazz,
	List<ResolvedFieldMapping> fields
) {

	public ResolvedClassMapping {
		fields = List.copyOf(fields);
	}
}
