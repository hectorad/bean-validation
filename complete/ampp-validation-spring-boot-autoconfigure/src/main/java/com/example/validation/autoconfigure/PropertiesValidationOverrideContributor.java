package com.example.validation.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.Ordered;

import com.example.validatingforminput.validation.ClassValidationOverride;
import com.example.validatingforminput.validation.ConstraintOverrideSet;
import com.example.validatingforminput.validation.FieldValidationOverride;
import com.example.validatingforminput.validation.ValidationOverrideContributor;
import com.example.validatingforminput.validation.ValidationProperties;

public class PropertiesValidationOverrideContributor implements ValidationOverrideContributor {

    private final ValidationProperties validationProperties;

    public PropertiesValidationOverrideContributor(ValidationProperties validationProperties) {
        this.validationProperties = validationProperties;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public List<ClassValidationOverride> getOverrides() {
        List<ClassValidationOverride> overrides = new ArrayList<>();
        for (ValidationProperties.ClassMapping classMapping : validationProperties.getBusinessValidationOverride()) {
            if (classMapping == null || classMapping.getFullClassName() == null) {
                continue;
            }
            overrides.add(toClassOverride(classMapping));
        }
        return overrides;
    }

    private ClassValidationOverride toClassOverride(ValidationProperties.ClassMapping classMapping) {
        List<FieldValidationOverride> fieldOverrides = new ArrayList<>();
        for (ValidationProperties.FieldMapping fieldMapping : classMapping.getFields()) {
            if (fieldMapping == null || fieldMapping.getFieldName() == null) {
                continue;
            }
            fieldOverrides.add(toFieldOverride(fieldMapping));
        }
        return new ClassValidationOverride(classMapping.getFullClassName(), fieldOverrides);
    }

    private FieldValidationOverride toFieldOverride(ValidationProperties.FieldMapping fieldMapping) {
        return new FieldValidationOverride(
            fieldMapping.getFieldName(),
            toConstraintOverrideSet(fieldMapping.getConstraints()));
    }

    private ConstraintOverrideSet toConstraintOverrideSet(ValidationProperties.Constraints constraints) {
        if (constraints == null) {
            return ConstraintOverrideSet.EMPTY;
        }
        return new ConstraintOverrideSet(
            toBooleanOverride(constraints.getNotNull()),
            toBooleanOverride(constraints.getNotBlank()),
            toNumericOverride(constraints.getMin()),
            toNumericOverride(constraints.getMax()),
            toDecimalOverride(constraints.getDecimalMin()),
            toDecimalOverride(constraints.getDecimalMax()),
            toSizeOverride(constraints.getSize()),
            toPatternOverride(constraints.getPattern()),
            toExtensionsOverride(constraints.getExtensions()));
    }

    private ConstraintOverrideSet.BooleanOverride toBooleanOverride(ValidationProperties.ToggleConstraint toggle) {
        if (toggle == null || toggle.getValue() == null) {
            return null;
        }
        return new ConstraintOverrideSet.BooleanOverride(toggle.getValue(), toggle.getMessage());
    }

    private ConstraintOverrideSet.NumericOverride toNumericOverride(ValidationProperties.NumericConstraint numeric) {
        if (numeric == null || numeric.getValue() == null) {
            return null;
        }
        return new ConstraintOverrideSet.NumericOverride(numeric.getValue(), numeric.getMessage());
    }

    private ConstraintOverrideSet.DecimalOverride toDecimalOverride(ValidationProperties.DecimalConstraint decimal) {
        if (decimal == null || (decimal.getValue() == null && decimal.getInclusive() == null)) {
            return null;
        }
        return new ConstraintOverrideSet.DecimalOverride(
            decimal.getValue(), decimal.getInclusive(), decimal.getMessage());
    }

    private ConstraintOverrideSet.SizeOverride toSizeOverride(ValidationProperties.SizeConstraint size) {
        if (size == null) {
            return null;
        }
        ConstraintOverrideSet.NumericOverride min = toNumericOverride(size.getMin());
        ConstraintOverrideSet.NumericOverride max = toNumericOverride(size.getMax());
        if (min == null && max == null) {
            return null;
        }
        return new ConstraintOverrideSet.SizeOverride(min, max);
    }

    private ConstraintOverrideSet.PatternOverride toPatternOverride(ValidationProperties.PatternConstraint pattern) {
        if (pattern == null) {
            return null;
        }
        List<String> regexes = pattern.getRegexes();
        List<String> flags = pattern.getFlags();
        if ((regexes == null || regexes.isEmpty()) && (flags == null || flags.isEmpty())) {
            return null;
        }
        return new ConstraintOverrideSet.PatternOverride(
            pattern.getRegexes(), pattern.getFlags(), pattern.getMessage());
    }

    private ConstraintOverrideSet.ExtensionsOverride toExtensionsOverride(
        ValidationProperties.ExtensionsConstraint extensions
    ) {
        if (extensions == null || extensions.getRules() == null || extensions.getRules().isEmpty()) {
            return null;
        }
        List<ConstraintOverrideSet.ExtensionRuleOverride> rules = new ArrayList<>();
        for (ValidationProperties.ExtensionRuleConstraint rule : extensions.getRules()) {
            rules.add(new ConstraintOverrideSet.ExtensionRuleOverride(
                rule.getJsonPath(), rule.getRegex(), rule.getMessage()));
        }
        return new ConstraintOverrideSet.ExtensionsOverride(rules);
    }
}
