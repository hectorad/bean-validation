package io.github.hectorad.validation.autoconfigure;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import io.github.hectorad.validation.ClassValidationOverride;
import io.github.hectorad.validation.ConstraintOverrideSet;
import io.github.hectorad.validation.FieldValidationOverride;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "hector.validation")
public class ValidationProperties {

    private boolean enabled = true;

    private boolean failOnError = true;

    @Valid
    private HttpBypass httpBypass = new HttpBypass();

    @Valid
    private Feign feign = new Feign();

    @Valid
    private Kafka kafka = new Kafka();

    @Valid
    private List<@NotNull @Valid ClassOverrideProperties> overrides = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    public HttpBypass getHttpBypass() {
        return httpBypass;
    }

    public void setHttpBypass(HttpBypass httpBypass) {
        this.httpBypass = defaultValue(httpBypass, HttpBypass::new);
    }

    public Feign getFeign() {
        return feign;
    }

    public void setFeign(Feign feign) {
        this.feign = defaultValue(feign, Feign::new);
    }

    public Kafka getKafka() {
        return kafka;
    }

    public void setKafka(Kafka kafka) {
        this.kafka = defaultValue(kafka, Kafka::new);
    }

    public List<ClassOverrideProperties> getOverrides() {
        return overrides;
    }

    public void setOverrides(List<ClassOverrideProperties> overrides) {
        this.overrides = copyList(overrides);
    }

    List<ClassValidationOverride> toValidationOverrides() {
        return overrides.stream().map(ClassOverrideProperties::toValidationOverride).toList();
    }

    public static class HttpBypass {

        static final String DEFAULT_HEADER_NAME = "X-Skip-Validation";

        static final String DEFAULT_HEADER_VALUE = "true";

        private boolean enabled = false;

        private String headerName = DEFAULT_HEADER_NAME;

        private String headerValue = DEFAULT_HEADER_VALUE;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = defaultString(headerName, DEFAULT_HEADER_NAME);
        }

        public String getHeaderValue() {
            return headerValue;
        }

        public void setHeaderValue(String headerValue) {
            this.headerValue = defaultString(headerValue, DEFAULT_HEADER_VALUE);
        }
    }

    public static class Feign {

        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Kafka {

        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class ClassOverrideProperties {

        @NotBlank
        private String className;

        @NotEmpty
        @Valid
        private List<@NotNull @Valid FieldOverrideProperties> fields = new ArrayList<>();

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = trimToNull(className);
        }

        public List<FieldOverrideProperties> getFields() {
            return fields;
        }

        public void setFields(List<FieldOverrideProperties> fields) {
            this.fields = copyList(fields);
        }

        ClassValidationOverride toValidationOverride() {
            return new ClassValidationOverride(
                className,
                fields.stream().map(FieldOverrideProperties::toValidationOverride).toList());
        }
    }

    public static class FieldOverrideProperties {

        @NotBlank
        private String fieldName;

        @Valid
        private ConstraintProperties constraints = new ConstraintProperties();

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = trimToNull(fieldName);
        }

        public ConstraintProperties getConstraints() {
            return constraints;
        }

        public void setConstraints(ConstraintProperties constraints) {
            this.constraints = defaultValue(constraints, ConstraintProperties::new);
        }

        FieldValidationOverride toValidationOverride() {
            return new FieldValidationOverride(fieldName, constraints.toConstraintOverrideSet());
        }
    }

    public static class ConstraintProperties {

        @Valid
        private ToggleConstraintProperties notNull = new ToggleConstraintProperties();

        @Valid
        private ToggleConstraintProperties notBlank = new ToggleConstraintProperties();

        @Valid
        private NumericConstraintProperties min = new NumericConstraintProperties();

        @Valid
        private NumericConstraintProperties max = new NumericConstraintProperties();

        @Valid
        private DecimalConstraintProperties decimalMin = new DecimalConstraintProperties();

        @Valid
        private DecimalConstraintProperties decimalMax = new DecimalConstraintProperties();

        @Valid
        private SizeConstraintProperties size = new SizeConstraintProperties();

        @Valid
        private PatternConstraintProperties pattern = new PatternConstraintProperties();

        @Valid
        private ExtensionsConstraintProperties extensions = new ExtensionsConstraintProperties();

        public ToggleConstraintProperties getNotNull() {
            return notNull;
        }

        public void setNotNull(ToggleConstraintProperties notNull) {
            this.notNull = defaultValue(notNull, ToggleConstraintProperties::new);
        }

        public ToggleConstraintProperties getNotBlank() {
            return notBlank;
        }

        public void setNotBlank(ToggleConstraintProperties notBlank) {
            this.notBlank = defaultValue(notBlank, ToggleConstraintProperties::new);
        }

        public NumericConstraintProperties getMin() {
            return min;
        }

        public void setMin(NumericConstraintProperties min) {
            this.min = defaultValue(min, NumericConstraintProperties::new);
        }

        public NumericConstraintProperties getMax() {
            return max;
        }

        public void setMax(NumericConstraintProperties max) {
            this.max = defaultValue(max, NumericConstraintProperties::new);
        }

        public DecimalConstraintProperties getDecimalMin() {
            return decimalMin;
        }

        public void setDecimalMin(DecimalConstraintProperties decimalMin) {
            this.decimalMin = defaultValue(decimalMin, DecimalConstraintProperties::new);
        }

        public DecimalConstraintProperties getDecimalMax() {
            return decimalMax;
        }

        public void setDecimalMax(DecimalConstraintProperties decimalMax) {
            this.decimalMax = defaultValue(decimalMax, DecimalConstraintProperties::new);
        }

        public SizeConstraintProperties getSize() {
            return size;
        }

        public void setSize(SizeConstraintProperties size) {
            this.size = defaultValue(size, SizeConstraintProperties::new);
        }

        public PatternConstraintProperties getPattern() {
            return pattern;
        }

        public void setPattern(PatternConstraintProperties pattern) {
            this.pattern = defaultValue(pattern, PatternConstraintProperties::new);
        }

        public ExtensionsConstraintProperties getExtensions() {
            return extensions;
        }

        public void setExtensions(ExtensionsConstraintProperties extensions) {
            this.extensions = defaultValue(extensions, ExtensionsConstraintProperties::new);
        }

        ConstraintOverrideSet toConstraintOverrideSet() {
            ConstraintOverrideSet constraintOverrideSet = new ConstraintOverrideSet();
            constraintOverrideSet.setNotNull(notNull.toConstraint());
            constraintOverrideSet.setNotBlank(notBlank.toConstraint());
            constraintOverrideSet.setMin(min.toConstraint());
            constraintOverrideSet.setMax(max.toConstraint());
            constraintOverrideSet.setDecimalMin(decimalMin.toConstraint());
            constraintOverrideSet.setDecimalMax(decimalMax.toConstraint());
            constraintOverrideSet.setSize(size.toConstraint());
            constraintOverrideSet.setPattern(pattern.toConstraint());
            constraintOverrideSet.setExtensions(extensions.toConstraint());
            return constraintOverrideSet;
        }
    }

    public static class ToggleConstraintProperties {

        private Boolean value;

        private String message;

        public Boolean getValue() {
            return value;
        }

        public void setValue(Boolean value) {
            this.value = value;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = trimToNull(message);
        }

        ConstraintOverrideSet.ToggleConstraint toConstraint() {
            ConstraintOverrideSet.ToggleConstraint constraint = new ConstraintOverrideSet.ToggleConstraint();
            constraint.setValue(value);
            constraint.setMessage(message);
            return constraint;
        }
    }

    public static class NumericConstraintProperties {

        private Long value;

        private String message;

        public Long getValue() {
            return value;
        }

        public void setValue(Long value) {
            this.value = value;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = trimToNull(message);
        }

        ConstraintOverrideSet.NumericConstraint toConstraint() {
            ConstraintOverrideSet.NumericConstraint constraint = new ConstraintOverrideSet.NumericConstraint();
            constraint.setValue(value);
            constraint.setMessage(message);
            return constraint;
        }
    }

    public static class DecimalConstraintProperties {

        private BigDecimal value;

        private Boolean inclusive;

        private String message;

        public BigDecimal getValue() {
            return value;
        }

        public void setValue(BigDecimal value) {
            this.value = value;
        }

        public Boolean getInclusive() {
            return inclusive;
        }

        public void setInclusive(Boolean inclusive) {
            this.inclusive = inclusive;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = trimToNull(message);
        }

        ConstraintOverrideSet.DecimalConstraint toConstraint() {
            ConstraintOverrideSet.DecimalConstraint constraint = new ConstraintOverrideSet.DecimalConstraint();
            constraint.setValue(value);
            constraint.setInclusive(inclusive);
            constraint.setMessage(message);
            return constraint;
        }
    }

    public static class SizeConstraintProperties {

        @Valid
        private NumericConstraintProperties min = new NumericConstraintProperties();

        @Valid
        private NumericConstraintProperties max = new NumericConstraintProperties();

        public NumericConstraintProperties getMin() {
            return min;
        }

        public void setMin(NumericConstraintProperties min) {
            this.min = defaultValue(min, NumericConstraintProperties::new);
        }

        public NumericConstraintProperties getMax() {
            return max;
        }

        public void setMax(NumericConstraintProperties max) {
            this.max = defaultValue(max, NumericConstraintProperties::new);
        }

        ConstraintOverrideSet.SizeConstraint toConstraint() {
            ConstraintOverrideSet.SizeConstraint constraint = new ConstraintOverrideSet.SizeConstraint();
            constraint.setMin(min.toConstraint());
            constraint.setMax(max.toConstraint());
            return constraint;
        }
    }

    public static class PatternConstraintProperties {

        private List<@NotBlank String> regexes = new ArrayList<>();

        private List<String> flags = new ArrayList<>();

        private String message;

        public List<String> getRegexes() {
            return regexes;
        }

        public void setRegexes(List<String> regexes) {
            this.regexes = copyList(regexes);
        }

        public List<String> getFlags() {
            return flags;
        }

        public void setFlags(List<String> flags) {
            this.flags = copyList(flags);
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = trimToNull(message);
        }

        ConstraintOverrideSet.PatternConstraint toConstraint() {
            ConstraintOverrideSet.PatternConstraint constraint = new ConstraintOverrideSet.PatternConstraint();
            constraint.setRegexes(regexes);
            constraint.setFlags(flags);
            constraint.setMessage(message);
            return constraint;
        }
    }

    public static class ExtensionsConstraintProperties {

        @Valid
        private List<@NotNull @Valid ExtensionRuleProperties> rules = new ArrayList<>();

        public List<ExtensionRuleProperties> getRules() {
            return rules;
        }

        public void setRules(List<ExtensionRuleProperties> rules) {
            this.rules = copyList(rules);
        }

        ConstraintOverrideSet.ExtensionsConstraint toConstraint() {
            ConstraintOverrideSet.ExtensionsConstraint constraint = new ConstraintOverrideSet.ExtensionsConstraint();
            constraint.setRules(rules.stream().map(ExtensionRuleProperties::toConstraint).toList());
            return constraint;
        }
    }

    public static class ExtensionRuleProperties {

        @NotBlank
        private String jsonPath;

        @NotBlank
        private String regex;

        private String message;

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

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = trimToNull(message);
        }

        ConstraintOverrideSet.ExtensionRule toConstraint() {
            ConstraintOverrideSet.ExtensionRule constraint = new ConstraintOverrideSet.ExtensionRule();
            constraint.setJsonPath(jsonPath);
            constraint.setRegex(regex);
            constraint.setMessage(message);
            return constraint;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String defaultString(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed == null ? defaultValue : trimmed;
    }

    private static <T> List<T> copyList(List<T> values) {
        return (values == null) ? new ArrayList<>() : new ArrayList<>(values);
    }

    private static <T> T defaultValue(T value, java.util.function.Supplier<T> defaultSupplier) {
        return (value == null) ? defaultSupplier.get() : value;
    }
}
