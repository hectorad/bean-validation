package com.example.validatingforminput.validation;

import java.util.List;

public record ClassValidationOverride(
    String className,
    List<FieldValidationOverride> fields
) {

    public ClassValidationOverride {
        fields = (fields == null) ? List.of() : List.copyOf(fields);
    }
}
