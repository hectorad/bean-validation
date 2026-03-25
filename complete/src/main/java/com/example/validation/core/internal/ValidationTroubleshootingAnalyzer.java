package com.example.validation.core.internal;

import com.example.validation.core.api.NumericBound;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

public class ValidationTroubleshootingAnalyzer implements SmartInitializingSingleton {

	private static final Logger log = LoggerFactory.getLogger(ValidationTroubleshootingAnalyzer.class);

	private final ValidationProperties validationProperties;

	private final GeneratedClassMetadataCache metadataCache;

	private final ConstraintMergeService constraintMergeService;

	public ValidationTroubleshootingAnalyzer(
		ValidationProperties validationProperties,
		GeneratedClassMetadataCache metadataCache,
		ConstraintMergeService constraintMergeService
	) {
		this.validationProperties = validationProperties;
		this.metadataCache = metadataCache;
		this.constraintMergeService = constraintMergeService;
	}

	@Override
	public void afterSingletonsInstantiated() {
		if (!log.isDebugEnabled()) {
			return;
		}

		Map<String, ValidationProperties.Constraints> configuredConstraintsByField = indexConfiguredConstraints();
		StringBuilder report = new StringBuilder();
		report.append("\n=== Validation Troubleshooting Report ===\n");

		for (ResolvedClassMapping classMapping : metadataCache.getResolvedMappings()) {
			report.append("Class: ").append(classMapping.className()).append('\n');

			for (ResolvedFieldMapping fieldMapping : classMapping.fields()) {
				String fieldKey = classMapping.className() + "." + fieldMapping.fieldName();
				ValidationProperties.Constraints configuredConstraints = configuredConstraintsByField.get(fieldKey);

				EffectiveFieldConstraints effective = constraintMergeService.merge(
					fieldMapping.baselineConstraints(),
					configuredConstraints,
					classMapping.className(),
					fieldMapping.fieldName());

				appendFieldReport(report, fieldMapping, configuredConstraints, effective);
			}
		}

		report.append("=== End Validation Troubleshooting Report ===");
		log.info(report.toString());
	}

	private void appendFieldReport(
		StringBuilder report,
		ResolvedFieldMapping fieldMapping,
		ValidationProperties.Constraints configured,
		EffectiveFieldConstraints effective
	) {
		BaselineFieldConstraints baseline = fieldMapping.baselineConstraints();
		if (configured == null) {
			configured = new ValidationProperties.Constraints();
		}

		report.append("  Field: ").append(fieldMapping.fieldName()).append('\n');

		appendBoolean(report, "notNull", baseline.notNull(),
			Boolean.TRUE.equals(configured.getNotNull().getValue()), effective.notNull(), effective.notNullMessage());
		appendBoolean(report, "notBlank", baseline.notBlank(),
			Boolean.TRUE.equals(configured.getNotBlank().getValue()), effective.notBlank(), effective.notBlankMessage());
		appendBound(report, "min", baseline.min(), configuredMin(configured), effective.min(), effective.minMessage());
		appendBound(report, "max", baseline.max(), configuredMax(configured), effective.max(), effective.maxMessage());
		appendSize(report, "sizeMin", baseline.sizeMin(), longToInt(configured.getSize().getMin().getValue()),
			effective.sizeMin(), effective.sizeMinMessage());
		appendSize(report, "sizeMax", baseline.sizeMax(), longToInt(configured.getSize().getMax().getValue()),
			effective.sizeMax(), effective.sizeMaxMessage());
		appendPatterns(report, baseline, configured, effective);
		appendExtensions(report, configured, effective);
	}

	private void appendBoolean(
		StringBuilder report, String name, boolean baseline, boolean configured, boolean effective, String message
	) {
		report.append("    ").append(padRight(name + ":", 14))
			.append("baseline=").append(padRight(String.valueOf(baseline), 6))
			.append(" | configured=").append(padRight(String.valueOf(configured), 6))
			.append(" | effective=").append(padRight(String.valueOf(effective), 6));
		if (message != null) {
			report.append(" | message=\"").append(message).append('"');
		}
		report.append('\n');
	}

	private void appendBound(
		StringBuilder report, String name, NumericBound baseline, NumericBound configured,
		NumericBound effective, String message
	) {
		report.append("    ").append(padRight(name + ":", 14))
			.append("baseline=").append(padRight(renderBound(baseline), 16))
			.append(" | configured=").append(padRight(renderBound(configured), 16))
			.append(" | effective=").append(padRight(renderBound(effective), 16));
		if (message != null) {
			report.append(" | message=\"").append(message).append('"');
		}
		appendWinner(report, baseline, configured, effective);
		report.append('\n');
	}

	private void appendSize(
		StringBuilder report, String name, Integer baseline, Integer configured,
		Integer effective, String message
	) {
		report.append("    ").append(padRight(name + ":", 14))
			.append("baseline=").append(padRight(renderInt(baseline), 6))
			.append(" | configured=").append(padRight(renderInt(configured), 6))
			.append(" | effective=").append(padRight(renderInt(effective), 6));
		if (message != null) {
			report.append(" | message=\"").append(message).append('"');
		}
		if (baseline != null && configured != null && effective != null) {
			if (effective.equals(configured) && !effective.equals(baseline)) {
				report.append(" [configured wins]");
			}
			else if (effective.equals(baseline) && !effective.equals(configured)) {
				report.append(" [baseline wins]");
			}
		}
		report.append('\n');
	}

	private void appendPatterns(
		StringBuilder report, BaselineFieldConstraints baseline,
		ValidationProperties.Constraints configured, EffectiveFieldConstraints effective
	) {
		int baselineCount = baseline.patterns().size();
		int configuredCount = configured.getPattern().getRegexes().size();
		int effectiveCount = effective.patterns().size();
		report.append("    ").append(padRight("patterns:", 14))
			.append("baseline=").append(padRight(String.valueOf(baselineCount), 6))
			.append(" | configured=").append(padRight(String.valueOf(configuredCount), 6))
			.append(" | effective=").append(effectiveCount);
		if (effectiveCount < baselineCount + configuredCount) {
			report.append(" (deduplicated)");
		}
		report.append('\n');
	}

	private void appendExtensions(
		StringBuilder report, ValidationProperties.Constraints configured, EffectiveFieldConstraints effective
	) {
		int configuredCount = configured.getExtensions().getRules().size();
		int effectiveCount = effective.extensionRules().size();
		if (configuredCount > 0 || effectiveCount > 0) {
			report.append("    ").append(padRight("extensions:", 14))
				.append("configured=").append(padRight(String.valueOf(configuredCount), 6))
				.append(" | effective=").append(effectiveCount).append('\n');
		}
	}

	private void appendWinner(StringBuilder report, NumericBound baseline, NumericBound configured, NumericBound effective) {
		if (baseline != null && configured != null && effective != null) {
			if (effective.equals(configured) && !effective.equals(baseline)) {
				report.append(" [configured wins]");
			}
			else if (effective.equals(baseline) && !effective.equals(configured)) {
				report.append(" [baseline wins]");
			}
		}
	}

	private NumericBound configuredMin(ValidationProperties.Constraints configured) {
		if (configured.getMin().getValue() != null) {
			return NumericBound.inclusive(configured.getMin().getValue());
		}
		if (configured.getDecimalMin().getValue() != null) {
			boolean inclusive = configured.getDecimalMin().getInclusive() == null || configured.getDecimalMin().getInclusive();
			return new NumericBound(configured.getDecimalMin().getValue(), inclusive);
		}
		return null;
	}

	private NumericBound configuredMax(ValidationProperties.Constraints configured) {
		if (configured.getMax().getValue() != null) {
			return NumericBound.inclusive(configured.getMax().getValue());
		}
		if (configured.getDecimalMax().getValue() != null) {
			boolean inclusive = configured.getDecimalMax().getInclusive() == null || configured.getDecimalMax().getInclusive();
			return new NumericBound(configured.getDecimalMax().getValue(), inclusive);
		}
		return null;
	}

	private Integer longToInt(Long value) {
		return (value == null) ? null : value.intValue();
	}

	private String renderBound(NumericBound bound) {
		if (bound == null) {
			return "none";
		}
		return bound.value().toPlainString() + (bound.inclusive() ? "(incl)" : "(excl)");
	}

	private String renderInt(Integer value) {
		return (value == null) ? "none" : value.toString();
	}

	private String padRight(String text, int width) {
		if (text.length() >= width) {
			return text;
		}
		return text + " ".repeat(width - text.length());
	}

	private Map<String, ValidationProperties.Constraints> indexConfiguredConstraints() {
		Map<String, ValidationProperties.Constraints> index = new HashMap<>();
		for (ValidationProperties.ClassMapping classMapping : validationProperties.getBusinessValidationOverride()) {
			for (ValidationProperties.FieldMapping fieldMapping : classMapping.getFields()) {
				String key = classMapping.getFullClassName() + "." + fieldMapping.getFieldName();
				index.put(key, fieldMapping.getConstraints());
			}
		}
		return index;
	}
}
