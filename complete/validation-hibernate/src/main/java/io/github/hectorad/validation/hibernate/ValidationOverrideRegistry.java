package io.github.hectorad.validation.hibernate;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.hectorad.validation.ClassValidationOverride;
import io.github.hectorad.validation.ConstraintOverrideSet;
import io.github.hectorad.validation.FieldValidationOverride;
import io.github.hectorad.validation.ValidationOverrideContributor;

public class ValidationOverrideRegistry {

    private final Map<String, Map<String, List<ConstraintOverrideSet>>> contributionsByTarget;

    public ValidationOverrideRegistry(List<ValidationOverrideContributor> contributors) {
        this.contributionsByTarget = indexContributions(contributors);
    }

    public Set<String> classNames() {
        return contributionsByTarget.keySet();
    }

    public Set<String> fieldNames(String className) {
        Map<String, List<ConstraintOverrideSet>> fields = contributionsByTarget.get(className);
        return (fields == null) ? Set.of() : fields.keySet();
    }

    public List<ConstraintOverrideSet> contributionsFor(String className, String fieldName) {
        Map<String, List<ConstraintOverrideSet>> fields = contributionsByTarget.get(className);
        if (fields == null) {
            return List.of();
        }
        List<ConstraintOverrideSet> contributions = fields.get(fieldName);
        return (contributions == null) ? List.of() : contributions;
    }

    private Map<String, Map<String, List<ConstraintOverrideSet>>> indexContributions(
        List<ValidationOverrideContributor> contributors
    ) {
        Map<String, Map<String, List<ConstraintOverrideSet>>> index = new LinkedHashMap<>();
        List<ValidationOverrideContributor> safeContributors =
            (contributors == null) ? List.of() : List.copyOf(contributors);
        for (ValidationOverrideContributor contributor : safeContributors) {
            registerContributor(index, contributor);
        }
        return deepCopy(index);
    }

    private void registerContributor(
        Map<String, Map<String, List<ConstraintOverrideSet>>> index,
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
        for (ClassValidationOverride classOverride : classOverrides) {
            if (classOverride == null || classOverride.className() == null) {
                throw new IllegalStateException("Each validation override class mapping must define a non-empty className.");
            }
            if (!contributorClassNames.add(classOverride.className())) {
                throw new IllegalStateException(
                    "Duplicate class mapping found in validation overrides: " + classOverride.className());
            }

            Set<String> fieldNames = new LinkedHashSet<>();
            for (FieldValidationOverride fieldOverride : classOverride.fields()) {
                if (fieldOverride == null || fieldOverride.fieldName() == null) {
                    throw new IllegalStateException(
                        "Each field override must define a non-empty fieldName for class: " + classOverride.className());
                }
                if (!fieldNames.add(fieldOverride.fieldName())) {
                    throw new IllegalStateException(
                        "Duplicate field mapping found for class " + classOverride.className() + ": " + fieldOverride.fieldName());
                }

                index
                    .computeIfAbsent(classOverride.className(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(fieldOverride.fieldName(), ignored -> new ArrayList<>())
                    .add(fieldOverride.constraints());
            }
        }
    }

    private Map<String, Map<String, List<ConstraintOverrideSet>>> deepCopy(
        Map<String, Map<String, List<ConstraintOverrideSet>>> source
    ) {
        Map<String, Map<String, List<ConstraintOverrideSet>>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<ConstraintOverrideSet>>> classEntry : source.entrySet()) {
            Map<String, List<ConstraintOverrideSet>> fields = new LinkedHashMap<>();
            for (Map.Entry<String, List<ConstraintOverrideSet>> fieldEntry : classEntry.getValue().entrySet()) {
                fields.put(fieldEntry.getKey(), List.copyOf(fieldEntry.getValue()));
            }
            copy.put(classEntry.getKey(), Collections.unmodifiableMap(fields));
        }
        return Collections.unmodifiableMap(copy);
    }
}
