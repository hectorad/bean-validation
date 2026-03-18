package io.github.hectorad.validation;

import java.util.List;

public record ClassValidationOverride(String className, List<FieldValidationOverride> fields) {

    public ClassValidationOverride {
        className = trimToNull(className);
        fields = (fields == null) ? List.of() : List.copyOf(fields);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
