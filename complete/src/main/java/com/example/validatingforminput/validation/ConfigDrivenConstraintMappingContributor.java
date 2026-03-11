package com.example.validatingforminput.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.hibernate.validator.cfg.ConstraintDef;
import org.hibernate.validator.cfg.ConstraintMapping;
import org.hibernate.validator.cfg.GenericConstraintDef;
import org.hibernate.validator.cfg.context.PropertyConstraintMappingContext;
import org.hibernate.validator.cfg.context.TypeConstraintMappingContext;
import org.hibernate.validator.cfg.defs.DecimalMaxDef;
import org.hibernate.validator.cfg.defs.DecimalMinDef;
import org.hibernate.validator.cfg.defs.NotBlankDef;
import org.hibernate.validator.cfg.defs.NotNullDef;
import org.hibernate.validator.cfg.defs.PatternDef;
import org.hibernate.validator.cfg.defs.SizeDef;
import org.hibernate.validator.spi.cfg.ConstraintMappingContributor;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import jakarta.validation.constraints.Pattern;

public class ConfigDrivenConstraintMappingContributor implements ConstraintMappingContributor {

	private final ConstraintMergeService constraintMergeService;

	private final List<ResolvedClassMapping> resolvedClassMappings;

	private final List<FieldConstraintContributor> fieldConstraintContributors;

	public ConfigDrivenConstraintMappingContributor(
		List<FieldConstraintContributor> fieldConstraintContributors,
		GeneratedClassMetadataCache generatedClassMetadataCache,
		ConstraintMergeService constraintMergeService
	) {
		List<FieldConstraintContributor> orderedContributors = new ArrayList<>(fieldConstraintContributors);
		AnnotationAwareOrderComparator.sort(orderedContributors);
		this.fieldConstraintContributors = List.copyOf(orderedContributors);
		this.constraintMergeService = constraintMergeService;
		this.resolvedClassMappings = generatedClassMetadataCache.getResolvedMappings();
	}

	@Override
	public void createConstraintMappings(ConstraintMappingBuilder builder) {
		for (ResolvedClassMapping resolvedClassMapping : resolvedClassMappings) {
			ConstraintMapping constraintMapping = builder.addConstraintMapping();
			TypeConstraintMappingContext<?> typeContext = constraintMapping.type(resolvedClassMapping.clazz());

			for (ResolvedFieldMapping resolvedFieldMapping : resolvedClassMapping.fields()) {
				EffectiveFieldConstraints effectiveConstraints = constraintMergeService.merge(
					resolvedFieldMapping.baselineConstraints(),
					contributedConstraints(
						resolvedClassMapping.className(),
						resolvedFieldMapping.fieldName(),
						resolvedFieldMapping.baselineConstraints()),
					resolvedClassMapping.className(),
					resolvedFieldMapping.fieldName());

				applyConstraints(typeContext, resolvedFieldMapping.fieldName(), effectiveConstraints);
			}
		}
	}

	private List<ValidationProperties.Constraints> contributedConstraints(
		String className,
		String fieldName,
		BaselineFieldConstraints baseline
	) {
		return fieldConstraintContributors.stream()
			.map(contributor -> contributor.contribute(className, fieldName, baseline))
			.flatMap(Optional::stream)
			.toList();
	}

	private void applyConstraints(
		TypeConstraintMappingContext<?> typeContext,
		String fieldName,
		EffectiveFieldConstraints effectiveConstraints
	) {
		PropertyConstraintMappingContext propertyContext = typeContext.field(fieldName).ignoreAnnotations(true);

		if (effectiveConstraints.notNull()) {
			applyConstraint(propertyContext, new NotNullDef(), effectiveConstraints.notNullMessage());
		}
		if (effectiveConstraints.notBlank()) {
			applyConstraint(propertyContext, new NotBlankDef(), effectiveConstraints.notBlankMessage());
		}
		if (effectiveConstraints.min() != null) {
			applyConstraint(
				propertyContext,
				new DecimalMinDef()
				.value(effectiveConstraints.min().value().toPlainString())
				.inclusive(effectiveConstraints.min().inclusive()),
				effectiveConstraints.minMessage());
		}
		if (effectiveConstraints.max() != null) {
			applyConstraint(
				propertyContext,
				new DecimalMaxDef()
				.value(effectiveConstraints.max().value().toPlainString())
				.inclusive(effectiveConstraints.max().inclusive()),
				effectiveConstraints.maxMessage());
		}
		if (effectiveConstraints.sizeMin() != null || effectiveConstraints.sizeMax() != null) {
			boolean splitByMessage = effectiveConstraints.sizeMin() != null
				&& effectiveConstraints.sizeMax() != null
				&& !Objects.equals(effectiveConstraints.sizeMinMessage(), effectiveConstraints.sizeMaxMessage());
			if (splitByMessage) {
				applyConstraint(
					propertyContext,
					new SizeDef().min(effectiveConstraints.sizeMin()),
					effectiveConstraints.sizeMinMessage());
				applyConstraint(
					propertyContext,
					new SizeDef().max(effectiveConstraints.sizeMax()),
					effectiveConstraints.sizeMaxMessage());
			}
			else {
				SizeDef sizeDef = new SizeDef();
				if (effectiveConstraints.sizeMin() != null) {
					sizeDef.min(effectiveConstraints.sizeMin());
				}
				if (effectiveConstraints.sizeMax() != null) {
					sizeDef.max(effectiveConstraints.sizeMax());
				}
				String mergedSizeMessage =
					(effectiveConstraints.sizeMin() != null) ? effectiveConstraints.sizeMinMessage() : effectiveConstraints.sizeMaxMessage();
				applyConstraint(propertyContext, sizeDef, mergedSizeMessage);
			}
		}
		for (PatternRule patternRule : effectiveConstraints.patterns()) {
			PatternDef patternDef = new PatternDef().regexp(patternRule.regex());
			if (!patternRule.flags().isEmpty()) {
				patternDef.flags(patternRule.flags().toArray(Pattern.Flag[]::new));
			}
			applyConstraint(propertyContext, patternDef, patternRule.message());
		}
		for (ExtensionRegexRule extensionRule : effectiveConstraints.extensionRules()) {
			applyConstraint(
				propertyContext,
				new GenericConstraintDef<>(ExtensionsJsonPathRegex.class)
					.param("jsonPath", extensionRule.jsonPath())
					.param("regex", extensionRule.regex()),
				extensionRule.message());
		}
	}

	private <D extends ConstraintDef<D, ?>> void applyConstraint(
		PropertyConstraintMappingContext propertyContext,
		D constraintDefinition,
		String message
	) {
		propertyContext.constraint((message == null) ? constraintDefinition : constraintDefinition.message(message));
	}
}
