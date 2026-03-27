package com.example.validation.core.internal;

import com.example.validation.core.spi.ClassValidationOverride;
import com.example.validation.core.spi.FieldValidationOverride;
import com.example.validation.core.spi.ValidationOverrideContributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ValidationOverrideRegistry {

	private final Map<String, Map<String, List<RegisteredConstraintOverride>>> contributionsByTarget;

	public ValidationOverrideRegistry(List<ValidationOverrideContributor> contributors) {
		this.contributionsByTarget = indexContributions(contributors);
	}

	public Set<String> classNames() {
		return contributionsByTarget.keySet();
	}

	public Set<String> fieldNames(String className) {
		Map<String, List<RegisteredConstraintOverride>> fields = contributionsByTarget.get(className);
		return (fields == null) ? Set.of() : fields.keySet();
	}

	public List<RegisteredConstraintOverride> contributionsFor(String className, String fieldName) {
		Map<String, List<RegisteredConstraintOverride>> fields = contributionsByTarget.get(className);
		if (fields == null) {
			return List.of();
		}
		List<RegisteredConstraintOverride> contributions = fields.get(fieldName);
		return (contributions == null) ? List.of() : contributions;
	}

	private Map<String, Map<String, List<RegisteredConstraintOverride>>> indexContributions(
		List<ValidationOverrideContributor> contributors
	) {
		Map<String, Map<String, List<RegisteredConstraintOverride>>> index = new LinkedHashMap<>();
		List<ValidationOverrideContributor> safeContributors =
			(contributors == null) ? List.of() : List.copyOf(contributors);
		for (ValidationOverrideContributor contributor : safeContributors) {
			registerContributor(index, contributor);
		}
		return deepCopy(index);
	}

	private void registerContributor(
		Map<String, Map<String, List<RegisteredConstraintOverride>>> index,
		ValidationOverrideContributor contributor
	) {
		if (contributor == null) {
			return;
		}

		List<ClassValidationOverride> classOverrides = contributor.getValidationOverrides();
		if (classOverrides == null || classOverrides.isEmpty()) {
			return;
		}

		Set<String> contributorClassNames = new LinkedHashSet<>();
		String sourceId = contributor.sourceId();
		for (ClassValidationOverride classOverride : classOverrides) {
			if (classOverride == null || classOverride.className() == null) {
				throw new IllegalStateException("Each validation override class mapping must define a non-empty className.");
			}
			if (!contributorClassNames.add(classOverride.className())) {
				throw new IllegalStateException(
					"Duplicate class mapping found in validation overrides for source "
						+ sourceId + ": " + classOverride.className());
			}

			Set<String> fieldNames = new LinkedHashSet<>();
			for (FieldValidationOverride fieldOverride : classOverride.fields()) {
				if (fieldOverride == null || fieldOverride.fieldName() == null) {
					throw new IllegalStateException(
						"Each field override must define a non-empty fieldName for class: " + classOverride.className());
				}
				if (!fieldNames.add(fieldOverride.fieldName())) {
					throw new IllegalStateException(
						"Duplicate field mapping found for class " + classOverride.className()
							+ " in source " + sourceId + ": " + fieldOverride.fieldName());
				}

				index
					.computeIfAbsent(classOverride.className(), ignored -> new LinkedHashMap<>())
					.computeIfAbsent(fieldOverride.fieldName(), ignored -> new ArrayList<>())
					.add(new RegisteredConstraintOverride(sourceId, fieldOverride.constraints()));
			}
		}
	}

	private Map<String, Map<String, List<RegisteredConstraintOverride>>> deepCopy(
		Map<String, Map<String, List<RegisteredConstraintOverride>>> source
	) {
		Map<String, Map<String, List<RegisteredConstraintOverride>>> copy = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, List<RegisteredConstraintOverride>>> classEntry : source.entrySet()) {
			Map<String, List<RegisteredConstraintOverride>> fields = new LinkedHashMap<>();
			for (Map.Entry<String, List<RegisteredConstraintOverride>> fieldEntry : classEntry.getValue().entrySet()) {
				fields.put(fieldEntry.getKey(), List.copyOf(fieldEntry.getValue()));
			}
			copy.put(classEntry.getKey(), Collections.unmodifiableMap(fields));
		}
		return Collections.unmodifiableMap(copy);
	}
}
