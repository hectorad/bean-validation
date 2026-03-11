package com.example.validatingforminput.validation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class PropertiesFieldConstraintContributor implements FieldConstraintContributor {

	private final Map<String, Map<String, ValidationProperties.Constraints>> constraintsByClassAndField;

	public PropertiesFieldConstraintContributor(ValidationProperties validationProperties) {
		this.constraintsByClassAndField = indexByClassAndField(validationProperties);
	}

	@Override
	public Optional<ValidationProperties.Constraints> contribute(
		String className,
		String fieldName,
		BaselineFieldConstraints baseline
	) {
		Map<String, ValidationProperties.Constraints> fieldConstraints = constraintsByClassAndField.get(className);
		if (fieldConstraints == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(fieldConstraints.get(fieldName));
	}

	private Map<String, Map<String, ValidationProperties.Constraints>> indexByClassAndField(
		ValidationProperties validationProperties
	) {
		Map<String, Map<String, ValidationProperties.Constraints>> classIndex = new LinkedHashMap<>();
		for (ValidationProperties.ClassMapping classMapping : validationProperties.getBusinessValidationOverride()) {
			if (classMapping == null || classMapping.getFullClassName() == null) {
				continue;
			}
			Map<String, ValidationProperties.Constraints> fieldIndex =
				classIndex.computeIfAbsent(classMapping.getFullClassName(), ignored -> new LinkedHashMap<>());
			for (ValidationProperties.FieldMapping fieldMapping : classMapping.getFields()) {
				if (fieldMapping == null || fieldMapping.getFieldName() == null) {
					continue;
				}
				fieldIndex.put(fieldMapping.getFieldName(), fieldMapping.getConstraints());
			}
		}

		Map<String, Map<String, ValidationProperties.Constraints>> immutableClassIndex = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, ValidationProperties.Constraints>> entry : classIndex.entrySet()) {
			immutableClassIndex.put(entry.getKey(), Map.copyOf(entry.getValue()));
		}
		return Map.copyOf(immutableClassIndex);
	}
}
