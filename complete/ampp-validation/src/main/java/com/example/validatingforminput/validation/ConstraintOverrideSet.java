package com.example.validatingforminput.validation;

import java.math.BigDecimal;
import java.util.List;

public record ConstraintOverrideSet(
    BooleanOverride notNull,
    BooleanOverride notBlank,
    NumericOverride min,
    NumericOverride max,
    DecimalOverride decimalMin,
    DecimalOverride decimalMax,
    SizeOverride size,
    PatternOverride pattern,
    ExtensionsOverride extensions
) {

    public static final ConstraintOverrideSet EMPTY = new ConstraintOverrideSet(
        null, null, null, null, null, null, null, null, null);

    public record BooleanOverride(Boolean value, String message) {}

    public record NumericOverride(Long value, String message) {}

    public record DecimalOverride(BigDecimal value, Boolean inclusive, String message) {}

    public record SizeOverride(NumericOverride min, NumericOverride max) {}

    public record PatternOverride(List<String> regexes, List<String> flags, String message) {
        public PatternOverride {
            regexes = (regexes == null) ? List.of() : List.copyOf(regexes);
            flags = (flags == null) ? List.of() : List.copyOf(flags);
        }
    }

    public record ExtensionsOverride(List<ExtensionRuleOverride> rules) {
        public ExtensionsOverride {
            rules = (rules == null) ? List.of() : List.copyOf(rules);
        }
    }

    public record ExtensionRuleOverride(String jsonPath, String regex, String message) {}
}
