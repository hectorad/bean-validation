package com.example.validation.core.internal;

import com.example.validation.core.spi.ClassValidationOverride;
import com.example.validation.core.spi.ConstraintOverrideSet;
import com.example.validation.core.spi.FieldValidationOverride;
import com.example.validation.core.spi.ValidationOverrideContributor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PropertiesValidationOverrideContributor implements ValidationOverrideContributor, Ordered {

	private static final Logger log = LoggerFactory.getLogger(PropertiesValidationOverrideContributor.class);

	private final ValidationProperties validationProperties;

	public PropertiesValidationOverrideContributor(ValidationProperties validationProperties) {
		this.validationProperties = validationProperties;
	}

	@Override
	public List<ClassValidationOverride> getValidationOverrides() {
		List<ClassValidationOverride> overrides = new ArrayList<>();
		for (ValidationProperties.ClassMapping classMapping : validationProperties.getBusinessValidationOverride()) {
			if (classMapping == null) {
				continue;
			}
			List<FieldValidationOverride> fieldOverrides = new ArrayList<>();
			for (ValidationProperties.FieldMapping fieldMapping : classMapping.getFields()) {
				if (fieldMapping == null) {
					continue;
				}
				try {
					fieldOverrides.add(new FieldValidationOverride(
						fieldMapping.getFieldName(),
						toConstraintOverrideSet(fieldMapping.getConstraints())));
				}
				catch (FieldConstraintConfigurationException exception) {
					log.warn(
						"Skipping validation override field mapping from source={} for class={}, field={} due to error in constraint[{}]: {}",
						sourceId(),
						classMapping.getFullClassName(),
						fieldMapping.getFieldName(),
						exception.constraintIndex(),
						exception.getMessage());
				}
			}
			overrides.add(new ClassValidationOverride(classMapping.getFullClassName(), fieldOverrides));
		}
		return List.copyOf(overrides);
	}

	@Override
	public String sourceId() {
		return "properties";
	}

	@Override
	public int getOrder() {
		return 0;
	}

	private ConstraintOverrideSet toConstraintOverrideSet(List<ValidationProperties.ConstraintMapping> constraints) {
		ConstraintOverrideSet target = new ConstraintOverrideSet();
		List<ValidationProperties.ConstraintMapping> safeConstraints =
			(constraints == null) ? List.of() : List.copyOf(constraints);
		for (int index = 0; index < safeConstraints.size(); index++) {
			ValidationProperties.ConstraintMapping constraint = safeConstraints.get(index);
			try {
				applyConstraint(target, constraint);
			}
			catch (FieldConstraintConfigurationException exception) {
				throw exception;
			}
			catch (RuntimeException exception) {
				throw new FieldConstraintConfigurationException(index, exception.getMessage(), exception);
			}
		}
		return target;
	}

	private void applyConstraint(
		ConstraintOverrideSet target,
		ValidationProperties.ConstraintMapping constraint
	) {
		if (constraint == null) {
			throw new IllegalArgumentException("constraint entry must not be null");
		}
		ConstraintType constraintType = ConstraintType.from(constraint.getConstraintType());
		ValidationProperties.ConstraintParameters params = constraint.getParams();
		String message = constraint.getMessage();
		switch (constraintType) {
			case NOT_NULL -> enable(target.getNotNull(), message);
			case NOT_BLANK -> enable(target.getNotBlank(), message);
			case MIN -> applyNumericConstraint(target.getMin(), requiredLong(params.getValue(), "params.value"), message, true);
			case MAX -> applyNumericConstraint(target.getMax(), requiredLong(params.getValue(), "params.value"), message, false);
			case DECIMAL_MIN -> applyDecimalConstraint(
				target.getDecimalMin(),
				requiredDecimal(params.getValue(), "params.value"),
				params.getInclusive(),
				message,
				true);
			case DECIMAL_MAX -> applyDecimalConstraint(
				target.getDecimalMax(),
				requiredDecimal(params.getValue(), "params.value"),
				params.getInclusive(),
				message,
				false);
			case SIZE -> applySizeConstraint(target.getSize(), params, message);
			case PATTERN -> applyPatternConstraint(target.getPattern(), params, message);
			case EXTENSIONS -> applyExtensionConstraint(target.getExtensions(), params, message);
		}
	}

	private void enable(ConstraintOverrideSet.ToggleConstraint target, String message) {
		if (Boolean.TRUE.equals(target.getValue())) {
			return;
		}
		target.setValue(true);
		target.setMessage(message);
	}

	private void applyNumericConstraint(
		ConstraintOverrideSet.NumericConstraint target,
		long candidateValue,
		String message,
		boolean higherWins
	) {
		Long currentValue = target.getValue();
		if (currentValue == null
			|| (higherWins && candidateValue > currentValue)
			|| (!higherWins && candidateValue < currentValue)) {
			target.setValue(candidateValue);
			target.setMessage(message);
		}
	}

	private void applyDecimalConstraint(
		ConstraintOverrideSet.DecimalConstraint target,
		BigDecimal candidateValue,
		Boolean inclusive,
		String message,
		boolean lowerBound
	) {
		BigDecimal currentValue = target.getValue();
		boolean candidateInclusive = inclusive == null || inclusive;
		if (currentValue == null) {
			target.setValue(candidateValue);
			target.setInclusive(inclusive);
			target.setMessage(message);
			return;
		}
		boolean currentInclusive = target.getInclusive() == null || target.getInclusive();
		int comparison = candidateValue.compareTo(currentValue);
		boolean candidateWins = lowerBound ? comparison > 0 : comparison < 0;
		if (!candidateWins && comparison == 0 && !candidateInclusive && currentInclusive) {
			candidateWins = true;
		}
		if (candidateWins) {
			target.setValue(candidateValue);
			target.setInclusive(inclusive);
			target.setMessage(message);
		}
	}

	private void applySizeConstraint(
		ConstraintOverrideSet.SizeConstraint target,
		ValidationProperties.ConstraintParameters params,
		String sharedMessage
	) {
		Long min = params.getMin();
		Long max = params.getMax();
		if (min == null && max == null) {
			throw new IllegalArgumentException("Size requires params.min or params.max");
		}
		if (min != null) {
			applyNumericConstraint(
				target.getMin(),
				min,
				firstNonNull(params.getMinMessage(), sharedMessage),
				true);
		}
		if (max != null) {
			applyNumericConstraint(
				target.getMax(),
				max,
				firstNonNull(params.getMaxMessage(), sharedMessage),
				false);
		}
	}

	private void applyPatternConstraint(
		ConstraintOverrideSet.PatternConstraint target,
		ValidationProperties.ConstraintParameters params,
		String message
	) {
		String regex = params.getRegexp();
		if (regex == null) {
			throw new IllegalArgumentException("Pattern requires params.regexp");
		}
		ConstraintOverrideSet.PatternRuleConfig rule = new ConstraintOverrideSet.PatternRuleConfig();
		rule.setRegex(regex);
		rule.setMessage(message);
		target.getRules().add(rule);
	}

	private void applyExtensionConstraint(
		ConstraintOverrideSet.ExtensionsConstraint target,
		ValidationProperties.ConstraintParameters params,
		String message
	) {
		String jsonPath = params.getJsonPath();
		String regex = params.getRegexp();
		if (jsonPath == null) {
			throw new IllegalArgumentException("Extensions requires params.jsonPath");
		}
		if (regex == null) {
			throw new IllegalArgumentException("Extensions requires params.regexp");
		}
		ConstraintOverrideSet.ExtensionRule rule = new ConstraintOverrideSet.ExtensionRule();
		rule.setJsonPath(jsonPath);
		rule.setRegex(regex);
		rule.setMessage(message);
		target.getRules().add(rule);
	}

	private BigDecimal requiredDecimal(BigDecimal value, String propertyName) {
		if (value == null) {
			throw new IllegalArgumentException(propertyName + " must be provided");
		}
		return value;
	}

	private long requiredLong(BigDecimal value, String propertyName) {
		BigDecimal required = requiredDecimal(value, propertyName);
		try {
			return required.longValueExact();
		}
		catch (ArithmeticException exception) {
			throw new IllegalArgumentException(propertyName + " must be an integer", exception);
		}
	}

	private String firstNonNull(String primary, String fallback) {
		return (primary != null) ? primary : fallback;
	}

	private enum ConstraintType {
		NOT_NULL,
		NOT_BLANK,
		MIN,
		MAX,
		DECIMAL_MIN,
		DECIMAL_MAX,
		SIZE,
		PATTERN,
		EXTENSIONS;

		private static ConstraintType from(String rawValue) {
			String normalized = normalize(rawValue);
			return switch (normalized) {
				case "notnull" -> NOT_NULL;
				case "notblank" -> NOT_BLANK;
				case "min" -> MIN;
				case "max" -> MAX;
				case "decimalmin" -> DECIMAL_MIN;
				case "decimalmax" -> DECIMAL_MAX;
				case "size" -> SIZE;
				case "pattern" -> PATTERN;
				case "extensions" -> EXTENSIONS;
				default -> throw new IllegalArgumentException("Unsupported constraintType: " + rawValue);
			};
		}

		private static String normalize(String rawValue) {
			if (rawValue == null) {
				return "";
			}
			return rawValue
				.replace("-", "")
				.replace("_", "")
				.replace(" ", "")
				.toLowerCase(Locale.ROOT);
		}
	}

	private static final class FieldConstraintConfigurationException extends RuntimeException {

		private final int constraintIndex;

		private FieldConstraintConfigurationException(int constraintIndex, String message, Throwable cause) {
			super(message, cause);
			this.constraintIndex = constraintIndex;
		}

		private int constraintIndex() {
			return constraintIndex;
		}
	}
}
