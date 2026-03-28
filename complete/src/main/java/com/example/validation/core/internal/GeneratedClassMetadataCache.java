package com.example.validation.core.internal;

import com.example.validation.core.api.ExtensionsJsonPathRegex;
import com.example.validation.core.api.JsonPathRegexRule;
import com.example.validation.core.api.NumericBound;
import com.example.validation.core.api.PatternRule;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import jakarta.validation.Constraint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.ConvertGroup;

public class GeneratedClassMetadataCache {

	private static final Logger log = LoggerFactory.getLogger(GeneratedClassMetadataCache.class);

	private static final Set<Class<? extends Annotation>> MODELED_CONSTRAINT_TYPES = Set.of(
		NotNull.class,
		NotBlank.class,
		Min.class,
		Max.class,
		DecimalMin.class,
		DecimalMax.class,
		Size.class,
		Pattern.class,
		ExtensionsJsonPathRegex.class);

	private final Map<String, ResolvedClassMapping> resolvedClassMappingsByName;

	private final List<ResolvedClassMapping> resolvedClassMappings;

	public GeneratedClassMetadataCache(ValidationProperties validationProperties) {
		this(new ValidationOverrideRegistry(List.of(new PropertiesValidationOverrideContributor(validationProperties))));
	}

	public GeneratedClassMetadataCache(ValidationOverrideRegistry validationOverrideRegistry) {
		this.resolvedClassMappingsByName = buildResolvedMappings(validationOverrideRegistry);
		this.resolvedClassMappings = List.copyOf(this.resolvedClassMappingsByName.values());
	}

	public List<ResolvedClassMapping> getResolvedMappings() {
		return resolvedClassMappings;
	}

	public ResolvedClassMapping getRequiredResolvedMapping(String className) {
		ResolvedClassMapping resolvedClassMapping = resolvedClassMappingsByName.get(className);
		if (resolvedClassMapping == null) {
			throw new IllegalStateException("Configured class is not present in startup metadata cache: " + className);
		}
		return resolvedClassMapping;
	}

	private Map<String, ResolvedClassMapping> buildResolvedMappings(ValidationOverrideRegistry validationOverrideRegistry) {
		Map<String, ResolvedClassMapping> mappingIndex = new LinkedHashMap<>();

		for (String className : validationOverrideRegistry.classNames()) {
			try {
				Class<?> clazz = resolveClass(className);
				Map<String, ResolvedFieldMapping> fieldMappingIndex = new LinkedHashMap<>();
				for (String fieldName : validationOverrideRegistry.fieldNames(className)) {
					List<RegisteredConstraintOverride> constraints =
						validationOverrideRegistry.contributionsFor(className, fieldName);
					try {
						ResolvedFieldMapping resolvedFieldMapping = resolveFieldMapping(
							clazz,
							className,
							fieldName,
							constraints);
						fieldMappingIndex.put(fieldName, resolvedFieldMapping);
					}
					catch (RuntimeException exception) {
						log.warn(
							"Skipping validation override field mapping for class={}, field={}, sources={} due to error: {}",
							className,
							fieldName,
							renderSources(constraints),
							exception.getMessage());
					}
				}

				if (!fieldMappingIndex.isEmpty()) {
					mappingIndex.put(
						className,
						new ResolvedClassMapping(className, clazz, new ArrayList<>(fieldMappingIndex.values())));
				}
			}
			catch (RuntimeException exception) {
				log.warn(
					"Skipping validation override class mapping for class={}, sources={} due to error: {}",
					className,
					renderSources(validationOverrideRegistry, className),
					exception.getMessage());
			}
		}

		return Collections.unmodifiableMap(mappingIndex);
	}

	private String renderSources(ValidationOverrideRegistry validationOverrideRegistry, String className) {
		return validationOverrideRegistry.fieldNames(className).stream()
			.flatMap(fieldName -> validationOverrideRegistry.contributionsFor(className, fieldName).stream())
			.map(RegisteredConstraintOverride::sourceId)
			.distinct()
			.toList()
			.toString();
	}

	private String renderSources(List<RegisteredConstraintOverride> constraints) {
		return constraints.stream()
			.map(RegisteredConstraintOverride::sourceId)
			.distinct()
			.toList()
			.toString();
	}

	private ResolvedFieldMapping resolveFieldMapping(
		Class<?> clazz,
		String className,
		String fieldName,
		List<RegisteredConstraintOverride> constraints
	) {
		Field field = resolveField(clazz, className, fieldName);
		Optional<Method> getter = findGetter(clazz, fieldName);
		BaselineFieldConstraints baseline = extractBaseline(field, getter, className);
		FieldValidationMetadata validationMetadata = extractValidationMetadata(field, getter);
		validateConstraints(className, field, baseline, constraints);
		return new ResolvedFieldMapping(fieldName, field.getType(), baseline, validationMetadata);
	}

	private Class<?> resolveClass(String className) {
		try {
			return ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
		}
		catch (ClassNotFoundException | LinkageError exception) {
			throw new IllegalStateException("Configured class was not found: " + className, exception);
		}
	}

	private Field resolveField(Class<?> clazz, String className, String fieldName) {
		Field field = ReflectionUtils.findField(clazz, fieldName);
		if (field != null) {
			return field;
		}
		throw new IllegalStateException(
			"Configured field was not found. class=" + className + ", field=" + fieldName);
	}

	private BaselineFieldConstraints extractBaseline(Field field, Optional<Method> getter, String className) {
		BaselineAccumulator baselineAccumulator = new BaselineAccumulator();
		collectAnnotationBaseline(field, baselineAccumulator, className, field.getName());
		getter.ifPresent(method -> collectAnnotationBaseline(method, baselineAccumulator, className, field.getName()));
		return baselineAccumulator.toBaseline();
	}

	private FieldValidationMetadata extractValidationMetadata(Field field, Optional<Method> getter) {
		ValidationMetadataAccumulator accumulator = new ValidationMetadataAccumulator();
		collectValidationMetadata(field, field.getAnnotatedType(), accumulator);
		getter.ifPresent(method -> collectValidationMetadata(method, method.getAnnotatedReturnType(), accumulator));
		return accumulator.toMetadata();
	}

	private void collectValidationMetadata(
		AnnotatedElement element,
		AnnotatedType annotatedType,
		ValidationMetadataAccumulator accumulator
	) {
		if (element == null) {
			return;
		}
		accumulator.cascaded |= element.isAnnotationPresent(Valid.class);
		accumulator.groupConversions.putAll(extractGroupConversions(element.getAnnotationsByType(ConvertGroup.class)));
		collectPassthroughConstraintAnnotations(element.getAnnotations(), accumulator.constraintAnnotations);
		collectContainerElementMetadata(annotatedType, List.of(), accumulator);
	}

	private Map<Class<?>, Class<?>> extractGroupConversions(ConvertGroup[] conversions) {
		Map<Class<?>, Class<?>> extracted = new LinkedHashMap<>();
		for (ConvertGroup conversion : conversions) {
			extracted.putIfAbsent(conversion.from(), conversion.to());
		}
		return extracted;
	}

	private void collectContainerElementMetadata(
		AnnotatedType annotatedType,
		List<Integer> path,
		ValidationMetadataAccumulator accumulator
	) {
		if (annotatedType == null || annotatedType instanceof AnnotatedArrayType) {
			return;
		}
		if (!(annotatedType instanceof AnnotatedParameterizedType parameterizedType)) {
			return;
		}

		AnnotatedType[] typeArguments = parameterizedType.getAnnotatedActualTypeArguments();
		for (int index = 0; index < typeArguments.length; index++) {
			AnnotatedType typeArgument = typeArguments[index];
			List<Integer> currentPath = appendPath(path, index);
			ContainerElementMetadataAccumulator containerAccumulator =
				accumulator.containerElements.computeIfAbsent(currentPath, ignored -> new ContainerElementMetadataAccumulator());
			containerAccumulator.cascaded |= typeArgument.isAnnotationPresent(Valid.class);
			containerAccumulator.groupConversions.putAll(extractGroupConversions(typeArgument.getAnnotationsByType(ConvertGroup.class)));
			collectPassthroughConstraintAnnotations(typeArgument.getAnnotations(), containerAccumulator.constraintAnnotations);
			collectContainerElementMetadata(typeArgument, currentPath, accumulator);
		}
	}

	private List<Integer> appendPath(List<Integer> path, int index) {
		List<Integer> nestedPath = new ArrayList<>(path.size() + 1);
		nestedPath.addAll(path);
		nestedPath.add(index);
		return List.copyOf(nestedPath);
	}

	private void collectPassthroughConstraintAnnotations(Annotation[] annotations, Set<Annotation> passthroughAnnotations) {
		for (Annotation annotation : annotations) {
			collectPassthroughConstraintAnnotation(annotation, passthroughAnnotations);
		}
	}

	private void collectPassthroughConstraintAnnotation(Annotation annotation, Set<Annotation> passthroughAnnotations) {
		Class<? extends Annotation> annotationType = annotation.annotationType();
		if (annotationType == Valid.class || annotationType == ConvertGroup.class || annotationType == ConvertGroup.List.class) {
			return;
		}
		if (annotationType.isAnnotationPresent(Constraint.class)) {
			if (!MODELED_CONSTRAINT_TYPES.contains(annotationType)) {
				passthroughAnnotations.add(annotation);
			}
			return;
		}

		Method valueMethod = findConstraintContainerValueMethod(annotationType);
		if (valueMethod == null) {
			return;
		}
		Annotation[] nestedAnnotations;
		try {
			ReflectionUtils.makeAccessible(valueMethod);
			nestedAnnotations = (Annotation[]) ReflectionUtils.invokeMethod(valueMethod, annotation);
		}
		catch (RuntimeException exception) {
			Object value = AnnotationUtils.getValue(annotation, AnnotationUtils.VALUE);
			if (!(value instanceof Annotation[] extractedAnnotations)) {
				throw new IllegalStateException(
					"Unable to inspect container constraint annotation: " + annotationType.getName(),
					exception);
			}
			nestedAnnotations = extractedAnnotations;
		}

		for (Annotation nestedAnnotation : nestedAnnotations) {
			collectPassthroughConstraintAnnotation(nestedAnnotation, passthroughAnnotations);
		}
	}

	private Method findConstraintContainerValueMethod(Class<? extends Annotation> annotationType) {
		Method valueMethod = ReflectionUtils.findMethod(annotationType, AnnotationUtils.VALUE);
		if (valueMethod == null) {
			return null;
		}
		Class<?> returnType = valueMethod.getReturnType();
		if (!returnType.isArray() || !Annotation.class.isAssignableFrom(returnType.getComponentType())) {
			return null;
		}
		@SuppressWarnings("unchecked")
		Class<? extends Annotation> nestedAnnotationType =
			(Class<? extends Annotation>) returnType.getComponentType();
		return nestedAnnotationType.isAnnotationPresent(Constraint.class) ? valueMethod : null;
	}

	private void validateConstraints(
		String className,
		Field field,
		BaselineFieldConstraints baseline,
		List<RegisteredConstraintOverride> constraints
	) {
		List<RegisteredConstraintOverride> effectiveConstraints = (constraints == null) ? List.of() : constraints;

		Class<?> rawFieldType = field.getType();
		Class<?> fieldType = ClassUtils.resolvePrimitiveIfNecessary(rawFieldType);
		String fieldName = field.getName();
		boolean extensionsConfigured = hasConfiguredExtensions(effectiveConstraints);

		if (isNotBlankEnabled(effectiveConstraints) && !supportsCharSequence(fieldType)) {
			throw unsupportedConstraint("notBlank", className, fieldName, fieldType);
		}
		if ((hasNumericBounds(baseline) || hasNumericBounds(effectiveConstraints)) && !supportsNumericBounds(fieldType)) {
			throw unsupportedConstraint("numeric bounds", className, fieldName, fieldType);
		}
		if (hasSizeBounds(effectiveConstraints) && !supportsContainerValue(rawFieldType)) {
			throw unsupportedConstraint("size", className, fieldName, rawFieldType);
		}
		if (hasConfiguredPatterns(effectiveConstraints) && !supportsCharSequence(fieldType)) {
			throw unsupportedConstraint("pattern", className, fieldName, fieldType);
		}
		if (extensionsConfigured && !supportsContainerValue(rawFieldType)) {
			throw unsupportedConstraint("extensions", className, fieldName, rawFieldType);
		}
	}

	private void collectAnnotationBaseline(
		AnnotatedElement element,
		BaselineAccumulator baselineAccumulator,
		String className,
		String fieldName
	) {
		if (element == null) {
			return;
		}

		if (element.isAnnotationPresent(NotNull.class)) {
			baselineAccumulator.notNull = true;
		}
		if (element.isAnnotationPresent(NotBlank.class)) {
			baselineAccumulator.notBlank = true;
		}

		for (Min min : element.getAnnotationsByType(Min.class)) {
			baselineAccumulator.min = NumericBound.stricterLower(baselineAccumulator.min, NumericBound.inclusive(min.value()));
		}
		for (Max max : element.getAnnotationsByType(Max.class)) {
			baselineAccumulator.max = NumericBound.stricterUpper(baselineAccumulator.max, NumericBound.inclusive(max.value()));
		}
		for (DecimalMin decimalMin : element.getAnnotationsByType(DecimalMin.class)) {
			baselineAccumulator.min = NumericBound.stricterLower(
				baselineAccumulator.min,
				parseDecimalAnnotationBound(decimalMin.value(), decimalMin.inclusive(), className, fieldName, "DecimalMin"));
		}
		for (DecimalMax decimalMax : element.getAnnotationsByType(DecimalMax.class)) {
			baselineAccumulator.max = NumericBound.stricterUpper(
				baselineAccumulator.max,
				parseDecimalAnnotationBound(decimalMax.value(), decimalMax.inclusive(), className, fieldName, "DecimalMax"));
		}
		for (Size size : element.getAnnotationsByType(Size.class)) {
			baselineAccumulator.sizeMin = maxNullable(baselineAccumulator.sizeMin, size.min());
			baselineAccumulator.sizeMax = minNullable(baselineAccumulator.sizeMax, size.max());
		}
		for (Pattern pattern : element.getAnnotationsByType(Pattern.class)) {
			baselineAccumulator.patterns.add(new PatternRule(pattern.regexp()));
		}
		for (ExtensionsJsonPathRegex extensionRule : element.getAnnotationsByType(ExtensionsJsonPathRegex.class)) {
			baselineAccumulator.extensionRules.add(new JsonPathRegexRule(
				extensionRule.jsonPath(),
				extensionRule.regex(),
				extensionRule.message()));
		}
	}

	private Optional<Method> findGetter(Class<?> clazz, String fieldName) {
		PropertyDescriptor propertyDescriptor = BeanUtils.getPropertyDescriptor(clazz, fieldName);
		if (propertyDescriptor == null) {
			return Optional.empty();
		}
		Method readMethod = propertyDescriptor.getReadMethod();
		return (readMethod != null && readMethod.getParameterCount() == 0) ? Optional.of(readMethod) : Optional.empty();
	}

	private NumericBound parseDecimalAnnotationBound(
		String value,
		boolean inclusive,
		String className,
		String fieldName,
		String annotationName
	) {
		try {
			return new NumericBound(new BigDecimal(value), inclusive);
		}
		catch (NumberFormatException exception) {
			throw new InvalidConstraintConfigurationException(
				"Invalid " + annotationName + " annotation. value could not be parsed for class="
					+ className + ", field=" + fieldName + ", value=" + value,
				exception);
		}
	}

	private Integer maxNullable(Integer first, int second) {
		return (first == null) ? second : Math.max(first, second);
	}

	private Integer minNullable(Integer first, int second) {
		return (first == null) ? second : Math.min(first, second);
	}

	private boolean isNotBlankEnabled(List<RegisteredConstraintOverride> constraints) {
		return constraints.stream()
			.map(RegisteredConstraintOverride::constraints)
			.anyMatch(constraint -> Boolean.TRUE.equals(constraint.getNotBlank().getValue()));
	}

	private boolean hasNumericBounds(BaselineFieldConstraints baseline) {
		return baseline.min() != null || baseline.max() != null;
	}

	private boolean hasNumericBounds(List<RegisteredConstraintOverride> constraints) {
		return constraints.stream()
			.map(RegisteredConstraintOverride::constraints)
			.anyMatch(constraint -> constraint.getMin().getValue() != null
				|| constraint.getMax().getValue() != null
				|| constraint.getDecimalMin().getValue() != null
				|| constraint.getDecimalMax().getValue() != null);
	}

	private boolean hasSizeBounds(List<RegisteredConstraintOverride> constraints) {
		return constraints.stream()
			.map(RegisteredConstraintOverride::constraints)
			.anyMatch(constraint -> constraint.getSize().getMin().getValue() != null
				|| constraint.getSize().getMax().getValue() != null);
	}

	private boolean hasConfiguredPatterns(List<RegisteredConstraintOverride> constraints) {
		return constraints.stream()
			.map(RegisteredConstraintOverride::constraints)
			.anyMatch(constraint -> !constraint.getPattern().getRegexes().isEmpty());
	}

	private boolean hasConfiguredExtensions(List<RegisteredConstraintOverride> constraints) {
		return constraints.stream()
			.map(RegisteredConstraintOverride::constraints)
			.anyMatch(constraint -> !constraint.getExtensions().getRules().isEmpty());
	}

	private boolean supportsCharSequence(Class<?> fieldType) {
		return CharSequence.class.isAssignableFrom(fieldType);
	}

	private boolean supportsNumericBounds(Class<?> fieldType) {
		return supportsCharSequence(fieldType)
			|| fieldType == BigDecimal.class
			|| fieldType == BigInteger.class
			|| fieldType == Byte.class
			|| fieldType == Short.class
			|| fieldType == Integer.class
			|| fieldType == Long.class;
	}

	private boolean supportsContainerValue(Class<?> fieldType) {
		return fieldType.isArray()
			|| CharSequence.class.isAssignableFrom(fieldType)
			|| java.util.Collection.class.isAssignableFrom(fieldType)
			|| java.util.Map.class.isAssignableFrom(fieldType);
	}

	private InvalidConstraintConfigurationException unsupportedConstraint(
		String constraintName,
		String className,
		String fieldName,
		Class<?> fieldType
	) {
		return new InvalidConstraintConfigurationException(
			"Constraint " + constraintName + " is not supported for class="
				+ className + ", field=" + fieldName + ", fieldType=" + fieldType.getName());
	}

	private static final class BaselineAccumulator {

		private boolean notNull;

		private boolean notBlank;

		private NumericBound min;

		private NumericBound max;

		private Integer sizeMin;

		private Integer sizeMax;

		private final List<PatternRule> patterns = new ArrayList<>();

		private final List<JsonPathRegexRule> extensionRules = new ArrayList<>();

		private BaselineFieldConstraints toBaseline() {
			return new BaselineFieldConstraints(notNull, notBlank, min, max, sizeMin, sizeMax, patterns, extensionRules);
		}
	}

	private static List<GroupConversionMapping> toGroupConversionMappings(Map<Class<?>, Class<?>> groupConversions) {
		return groupConversions.entrySet().stream()
			.map(entry -> new GroupConversionMapping(entry.getKey(), entry.getValue()))
			.toList();
	}

	private static final class ValidationMetadataAccumulator {

		private final Set<Annotation> constraintAnnotations = new LinkedHashSet<>();

		private boolean cascaded;

		private final Map<Class<?>, Class<?>> groupConversions = new LinkedHashMap<>();

		private final Map<List<Integer>, ContainerElementMetadataAccumulator> containerElements = new LinkedHashMap<>();

		private FieldValidationMetadata toMetadata() {
			List<ContainerElementValidationMetadata> containerMetadata = containerElements.entrySet().stream()
				.map(entry -> entry.getValue().toMetadata(entry.getKey()))
				.filter(containerElementValidationMetadata -> !containerElementValidationMetadata.isEmpty())
				.toList();
			return new FieldValidationMetadata(
				new ArrayList<>(constraintAnnotations),
				cascaded,
				toGroupConversionMappings(groupConversions),
				containerMetadata);
		}
	}

	private static final class ContainerElementMetadataAccumulator {

		private final Set<Annotation> constraintAnnotations = new LinkedHashSet<>();

		private boolean cascaded;

		private final Map<Class<?>, Class<?>> groupConversions = new LinkedHashMap<>();

		private ContainerElementValidationMetadata toMetadata(List<Integer> path) {
			return new ContainerElementValidationMetadata(
				path,
				new ArrayList<>(constraintAnnotations),
				cascaded,
				toGroupConversionMappings(groupConversions));
		}
	}
}
