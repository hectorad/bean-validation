package com.example.validation.core.internal;

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

    private boolean validationEnabled = true;

    private boolean failOnError = true;

    @Valid
    private RequestValidationBypass requestValidationBypass = new RequestValidationBypass();

    @Valid
    private FeignResponseValidation feignResponseValidation = new FeignResponseValidation();

    @Valid
    private KafkaConsumerValidation kafkaConsumerValidation = new KafkaConsumerValidation();

    @Valid
    private MessageValidation messageValidation = new MessageValidation();

    @Valid
    private List<@NotNull @Valid ClassMapping> businessValidationOverride = new ArrayList<>();

    public boolean isValidationEnabled() {
        return validationEnabled;
    }

    public void setValidationEnabled(boolean validationEnabled) {
        this.validationEnabled = validationEnabled;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    public RequestValidationBypass getRequestValidationBypass() {
        return requestValidationBypass;
    }

    public void setRequestValidationBypass(RequestValidationBypass requestValidationBypass) {
        this.requestValidationBypass = defaultValue(requestValidationBypass, RequestValidationBypass::new);
    }

    public FeignResponseValidation getFeignResponseValidation() {
        return feignResponseValidation;
    }

    public void setFeignResponseValidation(FeignResponseValidation feignResponseValidation) {
        this.feignResponseValidation = defaultValue(feignResponseValidation, FeignResponseValidation::new);
    }

    public KafkaConsumerValidation getKafkaConsumerValidation() {
        return kafkaConsumerValidation;
    }

    public void setKafkaConsumerValidation(KafkaConsumerValidation kafkaConsumerValidation) {
        this.kafkaConsumerValidation = defaultValue(kafkaConsumerValidation, KafkaConsumerValidation::new);
    }

    public MessageValidation getMessageValidation() {
        return messageValidation;
    }

    public void setMessageValidation(MessageValidation messageValidation) {
        this.messageValidation = defaultValue(messageValidation, MessageValidation::new);
    }

    public List<ClassMapping> getBusinessValidationOverride() {
        return businessValidationOverride;
    }

    public void setBusinessValidationOverride(List<ClassMapping> businessValidationOverride) {
        this.businessValidationOverride = copyList(businessValidationOverride);
    }

    public static class RequestValidationBypass {

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

    public static class FeignResponseValidation {

        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class KafkaConsumerValidation {

        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class MessageValidation {

        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
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
            this.fields = copyList(fields);
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
            this.constraints = defaultValue(constraints, Constraints::new);
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
            this.notNull = defaultValue(notNull, ToggleConstraint::new);
        }

        public ToggleConstraint getNotBlank() {
            return notBlank;
        }

        public void setNotBlank(ToggleConstraint notBlank) {
            this.notBlank = defaultValue(notBlank, ToggleConstraint::new);
        }

        public NumericConstraint getMin() {
            return min;
        }

        public void setMin(NumericConstraint min) {
            this.min = defaultValue(min, NumericConstraint::new);
        }

        public NumericConstraint getMax() {
            return max;
        }

        public void setMax(NumericConstraint max) {
            this.max = defaultValue(max, NumericConstraint::new);
        }

        public DecimalConstraint getDecimalMin() {
            return decimalMin;
        }

        public void setDecimalMin(DecimalConstraint decimalMin) {
            this.decimalMin = defaultValue(decimalMin, DecimalConstraint::new);
        }

        public DecimalConstraint getDecimalMax() {
            return decimalMax;
        }

        public void setDecimalMax(DecimalConstraint decimalMax) {
            this.decimalMax = defaultValue(decimalMax, DecimalConstraint::new);
        }

        public SizeConstraint getSize() {
            return size;
        }

        public void setSize(SizeConstraint size) {
            this.size = defaultValue(size, SizeConstraint::new);
        }

        public PatternConstraint getPattern() {
            return pattern;
        }

        public void setPattern(PatternConstraint pattern) {
            this.pattern = defaultValue(pattern, PatternConstraint::new);
        }

        public ExtensionsConstraint getExtensions() {
            return extensions;
        }

        public void setExtensions(ExtensionsConstraint extensions) {
            this.extensions = defaultValue(extensions, ExtensionsConstraint::new);
        }
    }

    public static class ToggleConstraint {

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
    }

    public static class NumericConstraint {

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
    }

    public static class DecimalConstraint {

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
            this.min = defaultValue(min, NumericConstraint::new);
        }

        public NumericConstraint getMax() {
            return max;
        }

        public void setMax(NumericConstraint max) {
            this.max = defaultValue(max, NumericConstraint::new);
        }
    }

    public static class PatternConstraint {

        private List<@NotBlank String> regexes = new ArrayList<>();

        private String message;

        public List<String> getRegexes() {
            return regexes;
        }

        public void setRegexes(List<String> regexes) {
            this.regexes = copyList(regexes);
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = trimToNull(message);
        }
    }

    public static class ExtensionsConstraint {

        @Valid
        private List<@NotNull @Valid ExtensionRuleConstraint> rules = new ArrayList<>();

        public List<ExtensionRuleConstraint> getRules() {
            return rules;
        }

        public void setRules(List<ExtensionRuleConstraint> rules) {
            this.rules = copyList(rules);
        }
    }

    public static class ExtensionRuleConstraint {

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
