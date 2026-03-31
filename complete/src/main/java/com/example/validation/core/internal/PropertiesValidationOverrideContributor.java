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
		ConstraintParser.ParsedConstraint parsedConstraint = ConstraintParser.parse(constraint);
		switch (parsedConstraint.constraintType()) {
			case NOT_NULL -> enable(target.getNotNull(), parsedConstraint.message());
			case NOT_BLANK -> enable(target.getNotBlank(), parsedConstraint.message());
			case MIN -> applyNumericConstraint(target.getMin(), parsedConstraint.value(), parsedConstraint.message(), true);
			case MAX -> applyNumericConstraint(target.getMax(), parsedConstraint.value(), parsedConstraint.message(), false);
			case DECIMAL_MIN -> applyDecimalConstraint(
				target.getDecimalMin(),
				parsedConstraint.decimalValue(),
				parsedConstraint.inclusive(),
				parsedConstraint.message(),
				true);
			case DECIMAL_MAX -> applyDecimalConstraint(
				target.getDecimalMax(),
				parsedConstraint.decimalValue(),
				parsedConstraint.inclusive(),
				parsedConstraint.message(),
				false);
			case SIZE -> applySizeConstraint(
				target.getSize(),
				parsedConstraint.min(),
				parsedConstraint.max(),
				parsedConstraint.message());
			case PATTERN -> applyPatternConstraint(
				target.getPattern(),
				parsedConstraint.regexp(),
				parsedConstraint.message());
			case EXTENSIONS -> applyExtensionConstraint(
				target.getExtensions(),
				parsedConstraint.jsonPath(),
				parsedConstraint.regexp(),
				parsedConstraint.message());
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
		Long min,
		Long max,
		String sharedMessage
	) {
		if (min != null) {
			applyNumericConstraint(
				target.getMin(),
				min,
				sharedMessage,
				true);
		}
		if (max != null) {
			applyNumericConstraint(
				target.getMax(),
				max,
				sharedMessage,
				false);
		}
	}

	private void applyPatternConstraint(
		ConstraintOverrideSet.PatternConstraint target,
		String regex,
		String message
	) {
		ConstraintOverrideSet.PatternRuleConfig rule = new ConstraintOverrideSet.PatternRuleConfig();
		rule.setRegex(regex);
		rule.setMessage(message);
		target.getRules().add(rule);
	}

	private void applyExtensionConstraint(
		ConstraintOverrideSet.ExtensionsConstraint target,
		String jsonPath,
		String regex,
		String message
	) {
		ConstraintOverrideSet.ExtensionRule rule = new ConstraintOverrideSet.ExtensionRule();
		rule.setJsonPath(jsonPath);
		rule.setRegex(regex);
		rule.setMessage(message);
		target.getRules().add(rule);
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
