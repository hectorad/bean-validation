package com.example.validation.core.spi;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ConstraintOverrideSet {

	private ToggleConstraint notNull = new ToggleConstraint();

	private ToggleConstraint notBlank = new ToggleConstraint();

	private NumericConstraint min = new NumericConstraint();

	private NumericConstraint max = new NumericConstraint();

	private DecimalConstraint decimalMin = new DecimalConstraint();

	private DecimalConstraint decimalMax = new DecimalConstraint();

	private SizeConstraint size = new SizeConstraint();

	private PatternConstraint pattern = new PatternConstraint();

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

		private NumericConstraint min = new NumericConstraint();

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

		private List<PatternRuleConfig> rules = new ArrayList<>();

		private String message;

		public List<String> getRegexes() {
			List<String> regexes = new ArrayList<>();
			for (PatternRuleConfig rule : rules) {
				regexes.add(rule.getRegex());
			}
			return regexes;
		}

		public void setRegexes(List<String> regexes) {
			List<PatternRuleConfig> convertedRules = new ArrayList<>();
			for (String regex : copyList(regexes)) {
				PatternRuleConfig rule = new PatternRuleConfig();
				rule.setRegex(regex);
				rule.setMessage(message);
				convertedRules.add(rule);
			}
			this.rules = convertedRules;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = trimToNull(message);
			for (PatternRuleConfig rule : rules) {
				rule.setMessage(this.message);
			}
		}

		public List<PatternRuleConfig> getRules() {
			return rules;
		}

		public void setRules(List<PatternRuleConfig> rules) {
			this.rules = copyList(rules);
		}
	}

	public static class PatternRuleConfig {

		private String regex;

		private String message;

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

	public static class ExtensionsConstraint {

		private List<ExtensionRule> rules = new ArrayList<>();

		public List<ExtensionRule> getRules() {
			return rules;
		}

		public void setRules(List<ExtensionRule> rules) {
			this.rules = copyList(rules);
		}
	}

	public static class ExtensionRule {

		private String jsonPath;

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

	private static <T> List<T> copyList(List<T> values) {
		return (values == null) ? new ArrayList<>() : new ArrayList<>(values);
	}

	private static <T> T defaultValue(T value, Supplier<T> defaultSupplier) {
		return (value == null) ? defaultSupplier.get() : value;
	}
}
