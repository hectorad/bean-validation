package com.example.validation.core.internal;

import com.example.validation.core.spi.ClassValidationOverride;
import com.example.validation.core.spi.ValidationOverrideContributor;

import org.springframework.core.Ordered;

import java.util.List;

public class PropertiesValidationOverrideContributor implements ValidationOverrideContributor, Ordered {

	private final ValidationProperties validationProperties;

	public PropertiesValidationOverrideContributor(ValidationProperties validationProperties) {
		this.validationProperties = validationProperties;
	}

	@Override
	public List<ClassValidationOverride> getValidationOverrides() {
		return validationProperties.toValidationOverrides();
	}

	@Override
	public String sourceId() {
		return "properties";
	}

	@Override
	public int getOrder() {
		return 0;
	}
}
