package com.example.validatingforminput.validation;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

public class ValidationTroubleshootingAnalyzer implements SmartInitializingSingleton {

	private static final Logger log = LoggerFactory.getLogger(ValidationTroubleshootingAnalyzer.class);

	private final List<ClassValidationOverride> mergedOverrides;

	private final GeneratedClassMetadataCache metadataCache;

	private final ConstraintMergeService constraintMergeService;

	public ValidationTroubleshootingAnalyzer(
		List<ClassValidationOverride> mergedOverrides,
		GeneratedClassMetadataCache metadataCache,
		ConstraintMergeService constraintMergeService
	) {
		this.mergedOverrides = mergedOverrides;
		this.metadataCache = metadataCache;
		this.constraintMergeService = constraintMergeService;
	}

	@Override
	public void afterSingletonsInstantiated() {
		if (!log.isDebugEnabled()) {
			return;
		}

		Map<String, ConstraintOverrideSet> overridesByField = indexOverrides();
		StringBuilder report = new StringBuilder();
		report.append("\n=== Validation Troubleshooting Report ===\n");

		for (ResolvedClassMapping classMapping : metadataCache.getResolvedMappings()) {
			report.append("Class: ").append(classMapping.className()).append('\n');

			for (ResolvedFieldMapping fieldMapping : classMapping.fields()) {
				String fieldKey = classMapping.className() + "." + fieldMapping.fieldName();
				ConstraintOverrideSet overrideSet = overridesByField.get(fieldKey);

				EffectiveFieldConstraints effective = constraintMergeService.merge(
					fieldMapping.baselineConstraints(),
					overrideSet,
					classMapping.className(),
					fieldMapping.fieldName());

				appendFieldReport(report, fieldMapping, overrideSet, effective);
			}
		}

		report.append("=== End Validation Troubleshooting Report ===");
		log.info(report.toString());
	}

	private void appendFieldReport(
		StringBuilder report,
		ResolvedFieldMapping fieldMapping,
		ConstraintOverrideSet configured,
		EffectiveFieldConstraints effective
	) {
		BaselineFieldConstraints baseline = fieldMapping.baselineConstraints();
		if (configured == null) {
			configured = ConstraintOverrideSet.EMPTY;
		}

		report.append("  Field: ").append(fieldMapping.fieldName()).append('\n');

		appendBoolean(report, "notNull", baseline.notNull(),
			configured.notNull() != null && Boolean.TRUE.equals(configured.notNull().value()),
			effective.notNull(), effective.notNullMessage());
		appendBoolean(report, "notBlank", baseline.notBlank(),
			configured.notBlank() != null && Boolean.TRUE.equals(configured.notBlank().value()),
			effective.notBlank(), effective.notBlankMessage());
		appendBound(report, "min", baseline.min(), configuredMin(configured), effective.min(), effective.minMessage());
		appendBound(report, "max", baseline.max(), configuredMax(configured), effective.max(), effective.maxMessage());
		appendSize(report, "sizeMin", baseline.sizeMin(), sizeOverrideValue(configured, true),
			effective.sizeMin(), effective.sizeMinMessage());
		appendSize(report, "sizeMax", baseline.sizeMax(), sizeOverrideValue(configured, false),
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
		ConstraintOverrideSet configured, EffectiveFieldConstraints effective
	) {
		int baselineCount = baseline.patterns().size();
		int configuredCount = (configured.pattern() != null && configured.pattern().regexes() != null)
			? configured.pattern().regexes().size() : 0;
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
		StringBuilder report, ConstraintOverrideSet configured, EffectiveFieldConstraints effective
	) {
		int configuredCount = (configured.extensions() != null && configured.extensions().rules() != null)
			? configured.extensions().rules().size() : 0;
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

	private NumericBound configuredMin(ConstraintOverrideSet configured) {
		if (configured.min() != null && configured.min().value() != null) {
			return NumericBound.inclusive(configured.min().value());
		}
		if (configured.decimalMin() != null && configured.decimalMin().value() != null) {
			boolean inclusive = configured.decimalMin().inclusive() == null || configured.decimalMin().inclusive();
			return new NumericBound(configured.decimalMin().value(), inclusive);
		}
		return null;
	}

	private NumericBound configuredMax(ConstraintOverrideSet configured) {
		if (configured.max() != null && configured.max().value() != null) {
			return NumericBound.inclusive(configured.max().value());
		}
		if (configured.decimalMax() != null && configured.decimalMax().value() != null) {
			boolean inclusive = configured.decimalMax().inclusive() == null || configured.decimalMax().inclusive();
			return new NumericBound(configured.decimalMax().value(), inclusive);
		}
		return null;
	}

	private Integer sizeOverrideValue(ConstraintOverrideSet configured, boolean min) {
		if (configured.size() == null) {
			return null;
		}
		ConstraintOverrideSet.NumericOverride bound = min ? configured.size().min() : configured.size().max();
		return (bound == null || bound.value() == null) ? null : bound.value().intValue();
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

	private Map<String, ConstraintOverrideSet> indexOverrides() {
		Map<String, ConstraintOverrideSet> index = new HashMap<>();
		for (ClassValidationOverride classOverride : mergedOverrides) {
			for (FieldValidationOverride fieldOverride : classOverride.fields()) {
				String key = classOverride.className() + "." + fieldOverride.fieldName();
				index.putIfAbsent(key, fieldOverride.constraints());
			}
		}
		return index;
	}
}
