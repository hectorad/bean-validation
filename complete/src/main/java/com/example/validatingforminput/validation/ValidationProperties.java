package com.example.validatingforminput.validation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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
        private SizeConstraint size = new SizeConstraint();

        @Valid
        private PatternConstraint pattern = new PatternConstraint();

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

        boolean hasConfiguredValues() {
            return isEnabled(notNull)
                    || isEnabled(notBlank)
                    || hasNumericValue(min)
                    || hasNumericValue(max)
                    || hasNumericValue(size.getMin())
                    || hasNumericValue(size.getMax())
                    || !pattern.getRegexes().isEmpty();
        }

        private boolean isEnabled(ToggleConstraint constraint) {
            return Boolean.TRUE.equals(constraint.getValue()) || Boolean.TRUE.equals(constraint.getHardValue());
        }

        private boolean hasNumericValue(NumericConstraint constraint) {
            return constraint.getValue() != null || constraint.getHardValue() != null;
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
