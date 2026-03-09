package com.example.validatingforminput.validation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "com.ampp")
public class ValidationProperties {

    @Valid
    private List<@NotNull @Valid ClassMapping> businessValidationOverride = new ArrayList<>();

    public List<ClassMapping> getBusinessValidationOverride() {
        return businessValidationOverride;
    }

    public void setBusinessValidationOverride(List<ClassMapping> businessValidationOverride) {
        this.businessValidationOverride = (businessValidationOverride == null) ? new ArrayList<>() : businessValidationOverride;
    }

    public static class ClassMapping {

        @NotBlank
        private String fullClassName;

        @NotEmpty
        @Valid
        private List<@NotNull @Valid FieldMapping> fields = new ArrayList<>();

        public String getFullClassName() {
            return fullClassName;
        }

        public void setFullClassName(String fullClassName) {
            this.fullClassName = trimToNull(fullClassName);
        }

        public List<FieldMapping> getFields() {
            return fields;
        }

        public void setFields(List<FieldMapping> fields) {
            this.fields = (fields == null) ? new ArrayList<>() : fields;
        }
    }

    public static class FieldMapping {

        @NotBlank
        private String fieldName;

        @Valid
        private Constraints constraints = new Constraints();

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = trimToNull(fieldName);
        }

        public Constraints getConstraints() {
            return constraints;
        }

        public void setConstraints(Constraints constraints) {
            this.constraints = (constraints == null) ? new Constraints() : constraints;
        }
    }

    public static class Constraints {

        @Valid
        private ToggleConstraint notNull = new ToggleConstraint();

        @Valid
        private ToggleConstraint notBlank = new ToggleConstraint();

        @Valid
        private NumericConstraint min = new NumericConstraint();

        @Valid
        private NumericConstraint max = new NumericConstraint();

        @Valid
        private DecimalConstraint decimalMin = new DecimalConstraint();

        @Valid
        private DecimalConstraint decimalMax = new DecimalConstraint();

        @Valid
        private SizeConstraint size = new SizeConstraint();

        @Valid
        private PatternConstraint pattern = new PatternConstraint();

        @Valid
        private ExtensionsConstraint extensions = new ExtensionsConstraint();

        public ToggleConstraint getNotNull() {
            return notNull;
        }

        public void setNotNull(ToggleConstraint notNull) {
            this.notNull = (notNull == null) ? new ToggleConstraint() : notNull;
        }

        public ToggleConstraint getNotBlank() {
            return notBlank;
        }

        public void setNotBlank(ToggleConstraint notBlank) {
            this.notBlank = (notBlank == null) ? new ToggleConstraint() : notBlank;
        }

        public NumericConstraint getMin() {
            return min;
        }

        public void setMin(NumericConstraint min) {
            this.min = (min == null) ? new NumericConstraint() : min;
        }

        public NumericConstraint getMax() {
            return max;
        }

        public void setMax(NumericConstraint max) {
            this.max = (max == null) ? new NumericConstraint() : max;
        }

        public DecimalConstraint getDecimalMin() {
            return decimalMin;
        }

        public void setDecimalMin(DecimalConstraint decimalMin) {
            this.decimalMin = (decimalMin == null) ? new DecimalConstraint() : decimalMin;
        }

        public DecimalConstraint getDecimalMax() {
            return decimalMax;
        }

        public void setDecimalMax(DecimalConstraint decimalMax) {
            this.decimalMax = (decimalMax == null) ? new DecimalConstraint() : decimalMax;
        }

        public SizeConstraint getSize() {
            return size;
        }

        public void setSize(SizeConstraint size) {
            this.size = (size == null) ? new SizeConstraint() : size;
        }

        public PatternConstraint getPattern() {
            return pattern;
        }

        public void setPattern(PatternConstraint pattern) {
            this.pattern = (pattern == null) ? new PatternConstraint() : pattern;
        }

        public ExtensionsConstraint getExtensions() {
            return extensions;
        }

        public void setExtensions(ExtensionsConstraint extensions) {
            this.extensions = (extensions == null) ? new ExtensionsConstraint() : extensions;
        }
    }

    public static class ToggleConstraint {

        private Boolean value;

        private Boolean hardValue;

        public Boolean getValue() {
            return value;
        }

        public void setValue(Boolean value) {
            this.value = value;
        }

        public Boolean getHardValue() {
            return hardValue;
        }

        public void setHardValue(Boolean hardValue) {
            this.hardValue = hardValue;
        }
    }

    public static class NumericConstraint {

        private Long value;

        private Long hardValue;

        public Long getValue() {
            return value;
        }

        public void setValue(Long value) {
            this.value = value;
        }

        public Long getHardValue() {
            return hardValue;
        }

        public void setHardValue(Long hardValue) {
            this.hardValue = hardValue;
        }
    }

    public static class DecimalConstraint {

        private BigDecimal value;

        private BigDecimal hardValue;

        private Boolean inclusive;

        private Boolean hardInclusive;

        public BigDecimal getValue() {
            return value;
        }

        public void setValue(BigDecimal value) {
            this.value = value;
        }

        public BigDecimal getHardValue() {
            return hardValue;
        }

        public void setHardValue(BigDecimal hardValue) {
            this.hardValue = hardValue;
        }

        public Boolean getInclusive() {
            return inclusive;
        }

        public void setInclusive(Boolean inclusive) {
            this.inclusive = inclusive;
        }

        public Boolean getHardInclusive() {
            return hardInclusive;
        }

        public void setHardInclusive(Boolean hardInclusive) {
            this.hardInclusive = hardInclusive;
        }
    }

    public static class SizeConstraint {

        @Valid
        private NumericConstraint min = new NumericConstraint();

        @Valid
        private NumericConstraint max = new NumericConstraint();

        public NumericConstraint getMin() {
            return min;
        }

        public void setMin(NumericConstraint min) {
            this.min = (min == null) ? new NumericConstraint() : min;
        }

        public NumericConstraint getMax() {
            return max;
        }

        public void setMax(NumericConstraint max) {
            this.max = (max == null) ? new NumericConstraint() : max;
        }
    }

    public static class PatternConstraint {

        private List<@NotBlank String> regexes = new ArrayList<>();

        public List<String> getRegexes() {
            return regexes;
        }

        public void setRegexes(List<String> regexes) {
            this.regexes = (regexes == null) ? new ArrayList<>() : regexes;
        }
    }

    public static class ExtensionsConstraint {

        @Valid
        private List<@NotNull @Valid ExtensionRuleConstraint> rules = new ArrayList<>();

        public List<ExtensionRuleConstraint> getRules() {
            return rules;
        }

        public void setRules(List<ExtensionRuleConstraint> rules) {
            this.rules = (rules == null) ? new ArrayList<>() : rules;
        }
    }

    public static class ExtensionRuleConstraint {

        @NotBlank
        private String jsonPath;

        @NotBlank
        private String regex;

        public String getJsonPath() {
            return jsonPath;
        }

        public void setJsonPath(String jsonPath) {
            this.jsonPath = trimToNull(jsonPath);
        }

        public String getRegex() {
            return regex;
        }

        public void setRegex(String regex) {
            this.regex = trimToNull(regex);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
