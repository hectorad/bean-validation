package com.example.validation.core.spi;

import java.util.List;

public interface ValidationOverrideContributor {

	List<ClassValidationOverride> getValidationOverrides();

	default String sourceId() {
		return getClass().getName();
	}
}
