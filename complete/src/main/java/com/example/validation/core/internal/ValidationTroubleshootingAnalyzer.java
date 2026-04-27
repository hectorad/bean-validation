package com.example.validation.core.internal;

import com.example.validation.core.api.NumericBound;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

public class ValidationTroubleshootingAnalyzer implements SmartInitializingSingleton {

	private static final Logger log = LoggerFactory.getLogger(ValidationTroubleshootingAnalyzer.class);

	private final ValidationOverrideRegistry validationOverrideRegistry;

	private final GeneratedClassMetadataCache metadataCache;

	private final ConstraintMergeService constraintMergeService;

	public ValidationTroubleshootingAnalyzer(
		ValidationOverrideRegistry validationOverrideRegistry,
		GeneratedClassMetadataCache metadataCache,
		ConstraintMergeService constraintMergeService
	) {
		this.validationOverrideRegistry = validationOverrideRegistry;
		this.metadataCache = metadataCache;
		this.constraintMergeService = constraintMergeService;
	}

	@Override
	public void afterSingletonsInstantiated() {
		if (!log.isDebugEnabled()) {
			return;
		}

		StringBuilder report = new StringBuilder();
		report.append("\n=== Validation Troubleshooting Report ===\n");
		Map<DeclaredFieldKey, List<DeclaredFieldEntry>> runtimeEntriesByField = groupRuntimeEntriesByField();

		for (ResolvedClassMapping classMapping : metadataCache.getResolvedMappings()) {
			report.append("Class: ").append(classMapping.className()).append('\n');

			for (ResolvedFieldMapping fieldMapping : classMapping.fields()) {
				List<DeclaredFieldEntry> runtimeEntries = runtimeEntriesFor(
					runtimeEntriesByField,
					classMapping,
					fieldMapping);
				List<RegisteredConstraintOverride> configuredConstraints = combinedContributions(runtimeEntries);
				ResolvedFieldMapping representative = runtimeEntries.getFirst().resolvedFieldMapping();
				String diagnosticClassName = renderConfiguredClassNames(runtimeEntries);
				try {
					EffectiveFieldConstraints effective = constraintMergeService.merge(
						representative.baselineConstraints(),
						configuredConstraints,
						diagnosticClassName,
						fieldMapping.fieldName());

					appendFieldReport(report, fieldMapping, configuredConstraints, effective);
				}
				catch (RuntimeException exception) {
					log.warn(
						"Skipping validation troubleshooting entry for class={}, field={}, sources={} due to error: {}",
						diagnosticClassName,
						fieldMapping.fieldName(),
						RegisteredConstraintOverride.renderSources(configuredConstraints),
						exception.getMessage());
					appendSkippedFieldReport(report, fieldMapping, configuredConstraints, exception.getMessage());
				}
			}
		}

		report.append("=== End Validation Troubleshooting Report ===");
		log.info(report.toString());
	}

	private Map<DeclaredFieldKey, List<DeclaredFieldEntry>> groupRuntimeEntriesByField() {
		Map<DeclaredFieldKey, List<DeclaredFieldEntry>> grouped = new LinkedHashMap<>();
		for (ResolvedClassMapping classMapping : metadataCache.getResolvedMappings()) {
			for (ResolvedFieldMapping fieldMapping : classMapping.fields()) {
				DeclaredFieldKey key = new DeclaredFieldKey(fieldMapping.declaringClass(), fieldMapping.fieldName());
				grouped
					.computeIfAbsent(key, ignored -> new ArrayList<>())
					.add(new DeclaredFieldEntry(classMapping.className(), fieldMapping));
			}
		}
		return grouped;
	}

	private List<DeclaredFieldEntry> runtimeEntriesFor(
		Map<DeclaredFieldKey, List<DeclaredFieldEntry>> runtimeEntriesByField,
		ResolvedClassMapping classMapping,
		ResolvedFieldMapping fieldMapping
	) {
		List<DeclaredFieldEntry> entries =
			runtimeEntriesByField.get(new DeclaredFieldKey(fieldMapping.declaringClass(), fieldMapping.fieldName()));
		if (entries != null) {
			return entries;
		}
		return List.of(new DeclaredFieldEntry(classMapping.className(), fieldMapping));
	}

	private List<RegisteredConstraintOverride> combinedContributions(List<DeclaredFieldEntry> entries) {
		List<RegisteredConstraintOverride> combined = new ArrayList<>();
		for (DeclaredFieldEntry entry : entries) {
			combined.addAll(validationOverrideRegistry.contributionsFor(
				entry.configuredClassName(),
				entry.resolvedFieldMapping().fieldName()));
		}
		return combined;
	}

	private String renderConfiguredClassNames(List<DeclaredFieldEntry> entries) {
		if (entries.size() == 1) {
			return entries.getFirst().configuredClassName();
		}
		return entries.stream().map(DeclaredFieldEntry::configuredClassName).distinct().toList().toString();
	}

	private record DeclaredFieldKey(Class<?> declaringClass, String fieldName) {
	}

	private record DeclaredFieldEntry(String configuredClassName, ResolvedFieldMapping resolvedFieldMapping) {
	}

	private void appendFieldReport(
		StringBuilder report,
		ResolvedFieldMapping fieldMapping,
		List<RegisteredConstraintOverride> configuredConstraints,
		EffectiveFieldConstraints effective
	) {
		BaselineFieldConstraints baseline = fieldMapping.baselineConstraints();

		report.append("  Field: ").append(fieldMapping.fieldName()).append('\n');
		report.append("    ").append(padRight("contributors:", 14))
			.append(configuredConstraints.size());
		if (!configuredConstraints.isEmpty()) {
			report.append(" ").append(RegisteredConstraintOverride.renderSources(configuredConstraints));
		}
		report.append('\n');

		appendBoolean(report, "notNull", baseline.notNull(), anyTrue(configuredConstraints, constraint -> constraint.getNotNull().getValue()),
			effective.notNull(), effective.notNullMessage());
		appendBoolean(report, "notBlank", baseline.notBlank(), anyTrue(configuredConstraints, constraint -> constraint.getNotBlank().getValue()),
			effective.notBlank(), effective.notBlankMessage());
		appendBound(report, "min", baseline.min(), strongestMin(configuredConstraints), effective.min(), effective.minMessage());
		appendBound(report, "max", baseline.max(), strongestMax(configuredConstraints), effective.max(), effective.maxMessage());
		appendSize(report, "sizeMin", baseline.sizeMin(), strongestSizeMin(configuredConstraints),
			effective.sizeMin(), effective.sizeMinMessage());
		appendSize(report, "sizeMax", baseline.sizeMax(), strongestSizeMax(configuredConstraints),
			effective.sizeMax(), effective.sizeMaxMessage());
		appendPatterns(report, baseline, configuredConstraints, effective);
		appendExtensions(report, configuredConstraints, effective);
	}

	private void appendSkippedFieldReport(
		StringBuilder report,
		ResolvedFieldMapping fieldMapping,
		List<RegisteredConstraintOverride> configuredConstraints,
		String error
	) {
		report.append("  Field: ").append(fieldMapping.fieldName()).append('\n');
		report.append("    ").append(padRight("contributors:", 14))
			.append(configuredConstraints.size());
		if (!configuredConstraints.isEmpty()) {
			report.append(" ").append(RegisteredConstraintOverride.renderSources(configuredConstraints));
		}
		report.append('\n');
		report.append("    ").append(padRight("status:", 14))
			.append("skipped | error=\"").append(error).append('"').append('\n');
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
		StringBuilder report,
		BaselineFieldConstraints baseline,
		List<RegisteredConstraintOverride> configuredConstraints,
		EffectiveFieldConstraints effective
	) {
		int baselineCount = baseline.patterns().size();
		int configuredCount = configuredConstraints.stream()
			.map(RegisteredConstraintOverride::constraints)
			.mapToInt(constraint -> constraint.getPattern().getRegexes().size())
			.sum();
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
		StringBuilder report,
		List<RegisteredConstraintOverride> configuredConstraints,
		EffectiveFieldConstraints effective
	) {
		int configuredCount = configuredConstraints.stream()
			.map(RegisteredConstraintOverride::constraints)
			.mapToInt(constraint -> constraint.getExtensions().getRules().size())
			.sum();
		int effectiveCount = effective.extensionRules().size();
		if (configuredCount > 0 || effectiveCount > 0) {
			report.append("    ").append(padRight("extensions:", 14))
				.append("configured=").append(padRight(String.valueOf(configuredCount), 6))
				.append(" | effective=").append(effectiveCount).append('\n');
		}
	}

	private boolean anyTrue(
		List<RegisteredConstraintOverride> configuredConstraints,
		java.util.function.Function<com.example.validation.core.spi.ConstraintOverrideSet, Boolean> accessor
	) {
		return configuredConstraints.stream()
			.map(RegisteredConstraintOverride::constraints)
			.map(accessor)
			.anyMatch(Boolean.TRUE::equals);
	}

	private NumericBound strongestMin(List<RegisteredConstraintOverride> configuredConstraints) {
		NumericBound result = null;
		for (RegisteredConstraintOverride configuredConstraint : configuredConstraints) {
			if (configuredConstraint.constraints().getMin().getValue() != null) {
				result = NumericBound.stricterLower(
					result,
					NumericBound.inclusive(configuredConstraint.constraints().getMin().getValue()));
			}
			if (configuredConstraint.constraints().getDecimalMin().getValue() != null) {
				boolean inclusive = configuredConstraint.constraints().getDecimalMin().getInclusive() == null
					|| configuredConstraint.constraints().getDecimalMin().getInclusive();
				result = NumericBound.stricterLower(
					result,
					new NumericBound(configuredConstraint.constraints().getDecimalMin().getValue(), inclusive));
			}
		}
		return result;
	}

	private NumericBound strongestMax(List<RegisteredConstraintOverride> configuredConstraints) {
		NumericBound result = null;
		for (RegisteredConstraintOverride configuredConstraint : configuredConstraints) {
			if (configuredConstraint.constraints().getMax().getValue() != null) {
				result = NumericBound.stricterUpper(
					result,
					NumericBound.inclusive(configuredConstraint.constraints().getMax().getValue()));
			}
			if (configuredConstraint.constraints().getDecimalMax().getValue() != null) {
				boolean inclusive = configuredConstraint.constraints().getDecimalMax().getInclusive() == null
					|| configuredConstraint.constraints().getDecimalMax().getInclusive();
				result = NumericBound.stricterUpper(
					result,
					new NumericBound(configuredConstraint.constraints().getDecimalMax().getValue(), inclusive));
			}
		}
		return result;
	}

	private Integer strongestSizeMin(List<RegisteredConstraintOverride> configuredConstraints) {
		Integer result = null;
		for (RegisteredConstraintOverride configuredConstraint : configuredConstraints) {
			Long value = configuredConstraint.constraints().getSize().getMin().getValue();
			if (value != null) {
				int candidate = Math.toIntExact(value);
				result = (result == null) ? candidate : Math.max(result, candidate);
			}
		}
		return result;
	}

	private Integer strongestSizeMax(List<RegisteredConstraintOverride> configuredConstraints) {
		Integer result = null;
		for (RegisteredConstraintOverride configuredConstraint : configuredConstraints) {
			Long value = configuredConstraint.constraints().getSize().getMax().getValue();
			if (value != null) {
				int candidate = Math.toIntExact(value);
				result = (result == null) ? candidate : Math.min(result, candidate);
			}
		}
		return result;
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
}
