package com.example.validatingforminput.validation;

public record FieldValidationOverride(
    String fieldName,
    ConstraintOverrideSet constraints
) {

    public FieldValidationOverride {
        if (constraints == null) {
            constraints = ConstraintOverrideSet.EMPTY;
        }
    }
}
