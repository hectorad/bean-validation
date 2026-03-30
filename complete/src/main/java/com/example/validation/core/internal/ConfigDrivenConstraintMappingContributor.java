package com.example.validation.core.internal;

import com.example.validation.core.api.ExtensionsJsonPathRegex;
import com.example.validation.core.api.JsonPathRegexRule;
import com.example.validation.core.api.PatternRule;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.hibernate.validator.cfg.ConstraintDef;
import org.hibernate.validator.cfg.ConstraintMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hibernate.validator.cfg.GenericConstraintDef;
import org.hibernate.validator.cfg.context.ContainerElementConstraintMappingContext;
import org.hibernate.validator.cfg.context.PropertyConstraintMappingContext;
import org.hibernate.validator.cfg.context.TypeConstraintMappingContext;
import org.hibernate.validator.cfg.defs.DecimalMaxDef;
import org.hibernate.validator.cfg.defs.DecimalMinDef;
import org.hibernate.validator.cfg.defs.NotBlankDef;
import org.hibernate.validator.cfg.defs.NotNullDef;
import org.hibernate.validator.cfg.defs.PatternDef;
import org.hibernate.validator.cfg.defs.SizeDef;
import org.hibernate.validator.spi.cfg.ConstraintMappingContributor;
import org.springframework.core.annotation.AnnotationUtils;

import jakarta.validation.Payload;

public class ConfigDrivenConstraintMappingContributor implements ConstraintMappingContributor {

	private static final Logger log = LoggerFactory.getLogger(ConfigDrivenConstraintMappingContributor.class);

	private final ValidationOverrideRegistry validationOverrideRegistry;

	private final ConstraintMergeService constraintMergeService;

	private final java.util.List<ResolvedClassMapping> resolvedClassMappings;

	public ConfigDrivenConstraintMappingContributor(
		ValidationOverrideRegistry validationOverrideRegistry,
		GeneratedClassMetadataCache generatedClassMetadataCache,
		ConstraintMergeService constraintMergeService
	) {
		this.validationOverrideRegistry = validationOverrideRegistry;
		this.constraintMergeService = constraintMergeService;
		this.resolvedClassMappings = generatedClassMetadataCache.getResolvedMappings();
	}

	@Override
	public void createConstraintMappings(ConstraintMappingBuilder builder) {
		for (ResolvedClassMapping resolvedClassMapping : resolvedClassMappings) {
			ConstraintMapping constraintMapping = builder.addConstraintMapping();
			TypeConstraintMappingContext<?> typeContext = constraintMapping.type(resolvedClassMapping.clazz());

			for (ResolvedFieldMapping resolvedFieldMapping : resolvedClassMapping.fields()) {
				List<RegisteredConstraintOverride> contributions = validationOverrideRegistry.contributionsFor(
					resolvedClassMapping.className(),
					resolvedFieldMapping.fieldName());
				try {
					EffectiveFieldConstraints effectiveConstraints = constraintMergeService.merge(
						resolvedFieldMapping.baselineConstraints(),
						contributions,
						resolvedClassMapping.className(),
						resolvedFieldMapping.fieldName());

					applyConstraints(typeContext, resolvedFieldMapping, effectiveConstraints);
				}
				catch (RuntimeException exception) {
					log.warn(
						"Skipping validation override constraint mapping for class={}, field={}, sources={} due to error: {}",
						resolvedClassMapping.className(),
						resolvedFieldMapping.fieldName(),
						RegisteredConstraintOverride.renderSources(contributions),
						exception.getMessage());
				}
			}
		}
	}

	private void applyConstraints(
		TypeConstraintMappingContext<?> typeContext,
		ResolvedFieldMapping resolvedFieldMapping,
		EffectiveFieldConstraints effectiveConstraints
	) {
		PropertyConstraintMappingContext propertyContext =
			typeContext.field(resolvedFieldMapping.fieldName()).ignoreAnnotations(true);
		applyValidationMetadata(propertyContext, resolvedFieldMapping.validationMetadata());

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
			applyConstraint(propertyContext, new PatternDef().regexp(patternRule.regex()), patternRule.message());
		}
		for (JsonPathRegexRule extensionRule : effectiveConstraints.extensionRules()) {
			applyConstraint(
				propertyContext,
				new GenericConstraintDef<>(ExtensionsJsonPathRegex.class)
					.param("jsonPath", extensionRule.jsonPath())
					.param("regex", extensionRule.regex()),
				extensionRule.message());
		}
	}

	private void applyValidationMetadata(
		PropertyConstraintMappingContext propertyContext,
		FieldValidationMetadata validationMetadata
	) {
		if (validationMetadata == null || validationMetadata.isEmpty()) {
			return;
		}
		applyConstraintAnnotations(propertyContext, validationMetadata.constraintAnnotations());
		applyCascadeMetadata(propertyContext, validationMetadata.cascaded(), validationMetadata.groupConversions());
		for (ContainerElementValidationMetadata containerElement : validationMetadata.containerElements()) {
			applyContainerElementValidationMetadata(propertyContext, containerElement);
		}
	}

	private void applyContainerElementValidationMetadata(
		PropertyConstraintMappingContext propertyContext,
		ContainerElementValidationMetadata containerElement
	) {
		ContainerElementConstraintMappingContext containerContext = propertyContext.containerElementType(
			containerElement.path().getFirst(),
			toNestedIndexes(containerElement.path()));
		applyConstraintAnnotations(containerContext, containerElement.constraintAnnotations());
		applyCascadeMetadata(containerContext, containerElement.cascaded(), containerElement.groupConversions());
	}

	private int[] toNestedIndexes(java.util.List<Integer> path) {
		if (path.size() <= 1) {
			return new int[0];
		}
		return path.subList(1, path.size()).stream().mapToInt(Integer::intValue).toArray();
	}

	private void applyConstraintAnnotations(
		org.hibernate.validator.cfg.context.Constrainable<?> constrainable,
		java.util.List<Annotation> annotations
	) {
		for (Annotation annotation : annotations) {
			constrainable.constraint(toConstraintDefinition(annotation));
		}
	}

	private void applyCascadeMetadata(
		org.hibernate.validator.cfg.context.Cascadable<?> cascadable,
		boolean cascaded,
		java.util.List<GroupConversionMapping> groupConversions
	) {
		if (cascaded) {
			cascadable.valid();
		}
		for (GroupConversionMapping groupConversion : groupConversions) {
			cascadable.convertGroup(groupConversion.from()).to(groupConversion.to());
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private ConstraintDef<?, ?> toConstraintDefinition(Annotation annotation) {
		GenericConstraintDef constraintDefinition = new GenericConstraintDef(annotation.annotationType());
		for (Map.Entry<String, Object> attribute : AnnotationUtils.getAnnotationAttributes(annotation, false, true).entrySet()) {
			Object value = attribute.getValue();
			switch (attribute.getKey()) {
				case "message" -> constraintDefinition.message((String) value);
				case "groups" -> constraintDefinition.groups((Class<?>[]) value);
				case "payload" -> constraintDefinition.payload((Class<? extends Payload>[]) value);
				default -> constraintDefinition.param(attribute.getKey(), copyAnnotationValue(value));
			}
		}
		return constraintDefinition;
	}

	private Object copyAnnotationValue(Object value) {
		if (value == null || !value.getClass().isArray()) {
			return value;
		}
		if (value instanceof boolean[] values) {
			return Arrays.copyOf(values, values.length);
		}
		if (value instanceof byte[] values) {
			return Arrays.copyOf(values, values.length);
		}
		if (value instanceof short[] values) {
			return Arrays.copyOf(values, values.length);
		}
		if (value instanceof int[] values) {
			return Arrays.copyOf(values, values.length);
		}
		if (value instanceof long[] values) {
			return Arrays.copyOf(values, values.length);
		}
		if (value instanceof float[] values) {
			return Arrays.copyOf(values, values.length);
		}
		if (value instanceof double[] values) {
			return Arrays.copyOf(values, values.length);
		}
		if (value instanceof char[] values) {
			return Arrays.copyOf(values, values.length);
		}
		return Arrays.copyOf((Object[]) value, ((Object[]) value).length);
	}

	private <D extends ConstraintDef<D, ?>> void applyConstraint(
		PropertyConstraintMappingContext propertyContext,
		D constraintDefinition,
		String message
	) {
		propertyContext.constraint((message == null) ? constraintDefinition : constraintDefinition.message(message));
	}

}
