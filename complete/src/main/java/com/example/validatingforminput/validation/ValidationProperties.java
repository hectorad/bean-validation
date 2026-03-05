package com.example.validatingforminput.validation;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "validation")
public class ValidationProperties {

	private List<ClassMapping> mappings = new ArrayList<>();

	public List<ClassMapping> getMappings() {
		return mappings;
	}

	public void setMappings(List<ClassMapping> mappings) {
		this.mappings = (mappings == null) ? new ArrayList<>() : mappings;
	}

	public static class ClassMapping {

		private String className;

		private List<FieldMapping> fields = new ArrayList<>();

		public String getClassName() {
			return className;
		}

		public void setClassName(String className) {
			this.className = className;
		}

		public List<FieldMapping> getFields() {
			return fields;
		}

		public void setFields(List<FieldMapping> fields) {
			this.fields = (fields == null) ? new ArrayList<>() : fields;
		}
	}

	public static class FieldMapping {

		private String fieldName;

		private Constraints constraints = new Constraints();

		public String getFieldName() {
			return fieldName;
		}

		public void setFieldName(String fieldName) {
			this.fieldName = fieldName;
		}

		public Constraints getConstraints() {
			return constraints;
		}

		public void setConstraints(Constraints constraints) {
			this.constraints = (constraints == null) ? new Constraints() : constraints;
		}
	}

	public static class Constraints {

		private ToggleConstraint notNull = new ToggleConstraint();

		private ToggleConstraint notBlank = new ToggleConstraint();

		private NumericConstraint min = new NumericConstraint();

		private NumericConstraint max = new NumericConstraint();

		private SizeConstraint size = new SizeConstraint();

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

		private NumericConstraint min = new NumericConstraint();

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

		private List<String> regexes = new ArrayList<>();

		public List<String> getRegexes() {
			return regexes;
		}

		public void setRegexes(List<String> regexes) {
			this.regexes = (regexes == null) ? new ArrayList<>() : regexes;
		}
	}
}
