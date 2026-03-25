package com.example.validation.core.internal;

import com.example.validation.core.api.FieldConstraintSet;
import com.example.validation.core.api.JsonPathRegexRule;
import com.example.validation.core.api.LowerBoundRule;
import com.example.validation.core.api.NotBlankRule;
import com.example.validation.core.api.NotNullRule;
import com.example.validation.core.api.NumericBound;
import com.example.validation.core.api.PatternRule;
import com.example.validation.core.api.SizeRule;
import com.example.validation.core.api.UpperBoundRule;
import com.example.validation.core.api.ValidationRule;
import com.example.validation.core.spi.ConstraintContribution;
import com.example.validation.core.spi.FieldConstraintContributor;
import com.example.validation.core.spi.ValidationFieldContext;

import java.util.*;

import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

@Order(0)
public class PropertiesFieldConstraintContributor implements FieldConstraintContributor {

	private static final String SOURCE_ID = "properties";

	private final Map<String, ValidationProperties.Constraints> constraintsByField;

	public PropertiesFieldConstraintContributor(ValidationProperties validationProperties) {
		this.constraintsByField = indexConstraints(validationProperties);
	}

	@Override
	public Optional<ConstraintContribution> contribute(ValidationFieldContext fieldContext) {
		ValidationProperties.Constraints constraints = constraintsByField.get(key(fieldContext.declaringClassName(), fieldContext.fieldName()));
		if (constraints == null) {
			return Optional.empty();
		}
		return Optional.of(fromConstraints(
			SOURCE_ID,
			constraints,
			fieldContext.declaringClassName(),
			fieldContext.fieldName()));
	}

	static ConstraintContribution fromConstraints(
		String sourceId,
		ValidationProperties.Constraints constraints,
		String className,
		String fieldName
	) {
		return new ConstraintContribution(
			sourceId,
			new FieldConstraintSet(toRules(constraints, className, fieldName)));
	}

	private Map<String, ValidationProperties.Constraints> indexConstraints(ValidationProperties validationProperties) {
		Map<String, ValidationProperties.Constraints> indexed = new LinkedHashMap<>();
		for (ValidationProperties.ClassMapping classMapping : validationProperties.getBusinessValidationOverride()) {
			for (ValidationProperties.FieldMapping fieldMapping : classMapping.getFields()) {
				indexed.put(key(classMapping.getFullClassName(), fieldMapping.getFieldName()), fieldMapping.getConstraints());
			}
		}
		return Map.copyOf(indexed);
	}

	private String key(String className, String fieldName) {
		return className + "." + fieldName;
	}

	private static List<ValidationRule> toRules(
		ValidationProperties.Constraints constraints,
		String className,
		String fieldName
	) {
		List<ValidationRule> rules = new ArrayList<>();
		if (Boolean.TRUE.equals(constraints.getNotNull().getValue())) {
			rules.add(new NotNullRule(constraints.getNotNull().getMessage()));
		}
		if (Boolean.TRUE.equals(constraints.getNotBlank().getValue())) {
			rules.add(new NotBlankRule(constraints.getNotBlank().getMessage()));
		}
		addBoundRules(rules, constraints, className, fieldName);
		addSizeRule(rules, constraints, className, fieldName);
		addPatternRules(rules, constraints, className, fieldName);
		addExtensionRules(rules, constraints, className, fieldName);
		return rules;
	}

	private static void addBoundRules(
		List<ValidationRule> rules,
		ValidationProperties.Constraints constraints,
		String className,
		String fieldName
	) {
		if (constraints.getMin().getValue() != null) {
			rules.add(new LowerBoundRule(NumericBound.inclusive(constraints.getMin().getValue()), constraints.getMin().getMessage()));
		}
		if (constraints.getDecimalMin().getInclusive() != null && constraints.getDecimalMin().getValue() == null) {
			throw invalid("decimal-min.inclusive requires decimal-min.value", className, fieldName);
		}
		if (constraints.getDecimalMin().getValue() != null) {
			boolean inclusive = constraints.getDecimalMin().getInclusive() == null || constraints.getDecimalMin().getInclusive();
			rules.add(new LowerBoundRule(new NumericBound(constraints.getDecimalMin().getValue(), inclusive), constraints.getDecimalMin().getMessage()));
		}
		if (constraints.getMax().getValue() != null) {
			rules.add(new UpperBoundRule(NumericBound.inclusive(constraints.getMax().getValue()), constraints.getMax().getMessage()));
		}
		if (constraints.getDecimalMax().getInclusive() != null && constraints.getDecimalMax().getValue() == null) {
			throw invalid("decimal-max.inclusive requires decimal-max.value", className, fieldName);
		}
		if (constraints.getDecimalMax().getValue() != null) {
			boolean inclusive = constraints.getDecimalMax().getInclusive() == null || constraints.getDecimalMax().getInclusive();
			rules.add(new UpperBoundRule(new NumericBound(constraints.getDecimalMax().getValue(), inclusive), constraints.getDecimalMax().getMessage()));
		}
	}

	private static void addSizeRule(
		List<ValidationRule> rules,
		ValidationProperties.Constraints constraints,
		String className,
		String fieldName
	) {
		Integer min = toSizeInteger(constraints.getSize().getMin().getValue(), className, fieldName, "size.min.value");
		Integer max = toSizeInteger(constraints.getSize().getMax().getValue(), className, fieldName, "size.max.value");
		if (min != null || max != null) {
			rules.add(new SizeRule(min, max, constraints.getSize().getMin().getMessage(), constraints.getSize().getMax().getMessage()));
		}
	}

	private static void addPatternRules(
		List<ValidationRule> rules,
		ValidationProperties.Constraints constraints,
		String className,
		String fieldName
	) {
		for (int index = 0; index < constraints.getPattern().getRegexes().size(); index++) {
			String regex = requireNonEmpty("pattern.regexes[" + index + "]", constraints.getPattern().getRegexes().get(index), className, fieldName);
			rules.add(new PatternRule(regex, constraints.getPattern().getMessage()));
		}
	}

	private static void addExtensionRules(
		List<ValidationRule> rules,
		ValidationProperties.Constraints constraints,
		String className,
		String fieldName
	) {
		for (int index = 0; index < constraints.getExtensions().getRules().size(); index++) {
			ValidationProperties.ExtensionRuleConstraint rule = constraints.getExtensions().getRules().get(index);
			if (rule == null) {
				throw invalid("extensions.rules[" + index + "] must not be null", className, fieldName);
			}
			String jsonPath = requireNonEmpty("extensions.rules[" + index + "].jsonPath", rule.getJsonPath(), className, fieldName);
			String regex = requireNonEmpty("extensions.rules[" + index + "].regex", rule.getRegex(), className, fieldName);
			rules.add(new JsonPathRegexRule(jsonPath, regex, rule.getMessage()));
		}
	}

	private static Integer toSizeInteger(Long value, String className, String fieldName, String propertyName) {
		if (value == null) {
			return null;
		}
		if (value < 0) {
			throw invalid(propertyName + " must be >= 0", className, fieldName);
		}
		try {
			return Math.toIntExact(value);
		}
		catch (ArithmeticException exception) {
			throw new InvalidConstraintConfigurationException(
				"Invalid size constraint. " + propertyName + " exceeds Integer.MAX_VALUE for class="
					+ className + ", field=" + fieldName + ", value=" + value,
				exception);
		}
	}

	private static String requireNonEmpty(String propertyName, String value, String className, String fieldName) {
		if (!StringUtils.hasText(value)) {
			String message;
			if (propertyName.contains("jsonPath")) {
				message = "jsonPath must be non-empty";
			}
			else if (propertyName.contains("regex")) {
				message = "regex must be non-empty";
			}
			else {
				message = propertyName + " must not be blank";
			}
			throw invalid(message, className, fieldName);
		}
		return value.trim();
	}

	private static InvalidConstraintConfigurationException invalid(String message, String className, String fieldName) {
		return new InvalidConstraintConfigurationException(
			"Invalid configured constraints for class=" + className + ", field=" + fieldName + ". " + message);
	}
}
