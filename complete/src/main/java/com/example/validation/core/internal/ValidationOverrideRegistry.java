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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidationOverrideRegistry {

	private static final Logger log = LoggerFactory.getLogger(ValidationOverrideRegistry.class);

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

		String sourceId = sourceId(contributor);
		List<ClassValidationOverride> classOverrides;
		try {
			classOverrides = contributor.getValidationOverrides();
		}
		catch (RuntimeException exception) {
			log.warn(
				"Skipping validation override contributor source={} due to error while loading overrides: {}",
				sourceId,
				exception.getMessage());
			return;
		}
		if (classOverrides == null || classOverrides.isEmpty()) {
			return;
		}

		Set<String> contributorClassNames = new LinkedHashSet<>();
		for (ClassValidationOverride classOverride : classOverrides) {
			if (classOverride == null || classOverride.className() == null) {
				log.warn(
					"Skipping validation override class mapping from source={} because className is missing.",
					sourceId);
				continue;
			}
			if (!contributorClassNames.add(classOverride.className())) {
				log.warn(
					"Skipping duplicate validation override class mapping from source={} for class={}.",
					sourceId,
					classOverride.className());
				continue;
			}

			Set<String> fieldNames = new LinkedHashSet<>();
			for (FieldValidationOverride fieldOverride : classOverride.fields()) {
				if (fieldOverride == null || fieldOverride.fieldName() == null) {
					log.warn(
						"Skipping validation override field mapping from source={} for class={} because fieldName is missing.",
						sourceId,
						classOverride.className());
					continue;
				}
				if (!fieldNames.add(fieldOverride.fieldName())) {
					log.warn(
						"Skipping duplicate validation override field mapping from source={} for class={}, field={}.",
						sourceId,
						classOverride.className(),
						fieldOverride.fieldName());
					continue;
				}

				index
					.computeIfAbsent(classOverride.className(), ignored -> new LinkedHashMap<>())
					.computeIfAbsent(fieldOverride.fieldName(), ignored -> new ArrayList<>())
					.add(new RegisteredConstraintOverride(sourceId, fieldOverride.constraints()));
			}
		}
	}

	private String sourceId(ValidationOverrideContributor contributor) {
		String declaredSourceId;
		try {
			declaredSourceId = contributor.sourceId();
		}
		catch (RuntimeException exception) {
			log.warn(
				"Contributor {} threw while resolving sourceId; using class name instead. error={}",
				contributor.getClass().getName(),
				exception.getMessage());
			return contributor.getClass().getName();
		}
		if (declaredSourceId == null || declaredSourceId.isBlank()) {
			log.warn(
				"Contributor {} returned a blank sourceId; using class name instead.",
				contributor.getClass().getName());
			return contributor.getClass().getName();
		}
		return declaredSourceId;
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
