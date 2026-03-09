package com.example.validatingforminput.validation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
import org.springframework.stereotype.Component;

import jakarta.validation.constraints.Pattern;

@Component
public class ConfigDrivenConstraintMappingContributor implements ConstraintMappingContributor {

	private final ValidationProperties validationProperties;

	private final ConstraintMergeService constraintMergeService;

	private final java.util.List<ResolvedClassMapping> resolvedClassMappings;

	public ConfigDrivenConstraintMappingContributor(
		ValidationProperties validationProperties,
		GeneratedClassMetadataCache generatedClassMetadataCache,
		ConstraintMergeService constraintMergeService
	) {
		this.validationProperties = validationProperties;
		this.constraintMergeService = constraintMergeService;
		this.resolvedClassMappings = generatedClassMetadataCache.getResolvedMappings();
	}

	@Override
	public void createConstraintMappings(ConstraintMappingBuilder builder) {
		Map<String, ValidationProperties.ClassMapping> configuredClasses = indexByClass(validationProperties);

		for (ResolvedClassMapping resolvedClassMapping : resolvedClassMappings) {
			ConstraintMapping constraintMapping = builder.addConstraintMapping();
			TypeConstraintMappingContext<?> typeContext = constraintMapping.type(resolvedClassMapping.clazz());

			Map<String, ValidationProperties.FieldMapping> configuredFields =
				indexByField(configuredClasses.get(resolvedClassMapping.className()));

			for (ResolvedFieldMapping resolvedFieldMapping : resolvedClassMapping.fields()) {
				ValidationProperties.FieldMapping configuredField = configuredFields.get(resolvedFieldMapping.fieldName());
				ValidationProperties.Constraints configuredConstraints =
					(configuredField == null) ? null : configuredField.getConstraints();

				EffectiveFieldConstraints effectiveConstraints = constraintMergeService.merge(
					resolvedFieldMapping.baselineConstraints(),
					configuredConstraints,
					resolvedClassMapping.className(),
					resolvedFieldMapping.fieldName());

				applyConstraints(typeContext, resolvedFieldMapping.fieldName(), effectiveConstraints);
			}
		}
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

	private Map<String, ValidationProperties.ClassMapping> indexByClass(ValidationProperties properties) {
		Map<String, ValidationProperties.ClassMapping> classIndex = new HashMap<>();
		for (ValidationProperties.ClassMapping classMapping : properties.getBusinessValidationOverride()) {
			if (classMapping != null && classMapping.getFullClassName() != null) {
				classIndex.put(classMapping.getFullClassName(), classMapping);
			}
		}
		return classIndex;
	}

	private Map<String, ValidationProperties.FieldMapping> indexByField(ValidationProperties.ClassMapping classMapping) {
		Map<String, ValidationProperties.FieldMapping> fieldIndex = new HashMap<>();
		if (classMapping == null) {
			return fieldIndex;
		}
		for (ValidationProperties.FieldMapping fieldMapping : classMapping.getFields()) {
			if (fieldMapping != null && fieldMapping.getFieldName() != null) {
				fieldIndex.put(fieldMapping.getFieldName(), fieldMapping);
			}
		}
		return fieldIndex;
	}
}
