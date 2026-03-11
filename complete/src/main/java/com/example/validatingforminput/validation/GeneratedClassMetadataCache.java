package com.example.validatingforminput.validation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.util.StringUtils;

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
		this.resolvedClassMappingsByName = buildResolvedMappings(validationProperties);
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

	private Map<String, ResolvedClassMapping> buildResolvedMappings(ValidationProperties validationProperties) {
		Map<String, ResolvedClassMapping> mappingIndex = new LinkedHashMap<>();

		for (ValidationProperties.ClassMapping classMapping : validationProperties.getBusinessValidationOverride()) {
			String className = normalizeClassName(classMapping);
			if (mappingIndex.containsKey(className)) {
				throw new IllegalStateException("Duplicate class mapping found in validation configuration: " + className);
			}

			Class<?> clazz = resolveClass(className);
			Map<String, ResolvedFieldMapping> fieldMappingIndex = new LinkedHashMap<>();
			for (ValidationProperties.FieldMapping fieldMapping : classMapping.getFields()) {
				String fieldName = normalizeFieldName(className, fieldMapping);
				if (fieldMappingIndex.containsKey(fieldName)) {
					throw new IllegalStateException("Duplicate field mapping found for class " + className + ": " + fieldName);
				}

				ResolvedFieldMapping resolvedFieldMapping = resolveFieldMapping(clazz, className, fieldMapping);
				fieldMappingIndex.put(fieldName, resolvedFieldMapping);
			}

			mappingIndex.put(
				className,
				new ResolvedClassMapping(className, clazz, new ArrayList<>(fieldMappingIndex.values())));
		}

		return Collections.unmodifiableMap(mappingIndex);
	}

	private ResolvedFieldMapping resolveFieldMapping(
		Class<?> clazz,
		String className,
		ValidationProperties.FieldMapping fieldMapping
	) {
		String fieldName = fieldMapping.getFieldName();
		Field field = resolveField(clazz, className, fieldName);
		BaselineFieldConstraints baseline = extractBaseline(clazz, field, className);
		FieldValidationMetadata validationMetadata = extractValidationMetadata(clazz, field);
		validateConstraints(className, field, baseline, fieldMapping.getConstraints());
		return new ResolvedFieldMapping(fieldName, baseline, validationMetadata);
	}

	private String normalizeClassName(ValidationProperties.ClassMapping classMapping) {
		if (classMapping == null || !StringUtils.hasText(classMapping.getFullClassName())) {
			throw new IllegalStateException("Each validation class mapping must define a non-empty fullClassName.");
		}
		return classMapping.getFullClassName().trim();
	}

	private String normalizeFieldName(String className, ValidationProperties.FieldMapping fieldMapping) {
		if (fieldMapping == null || !StringUtils.hasText(fieldMapping.getFieldName())) {
			throw new IllegalStateException(
				"Each field mapping must define a non-empty fieldName for class: " + className);
		}
		return fieldMapping.getFieldName().trim();
	}

	private Class<?> resolveClass(String className) {
		try {
			return Class.forName(className);
		}
		catch (ClassNotFoundException exception) {
			throw new IllegalStateException("Configured class was not found: " + className, exception);
		}
	}

	private Field resolveField(Class<?> clazz, String className, String fieldName) {
		Class<?> current = clazz;
		while (current != null && current != Object.class) {
			try {
				return current.getDeclaredField(fieldName);
			}
			catch (NoSuchFieldException ignored) {
				current = current.getSuperclass();
			}
		}
		throw new IllegalStateException(
			"Configured field was not found. class=" + className + ", field=" + fieldName);
	}

	private BaselineFieldConstraints extractBaseline(Class<?> clazz, Field field, String className) {
		BaselineAccumulator baselineAccumulator = new BaselineAccumulator();
		collectAnnotationBaseline(field, baselineAccumulator, className, field.getName());
		findGetter(clazz, field.getName())
			.ifPresent(method -> collectAnnotationBaseline(method, baselineAccumulator, className, field.getName()));
		return baselineAccumulator.toBaseline();
	}

	private FieldValidationMetadata extractValidationMetadata(Class<?> clazz, Field field) {
		ValidationMetadataAccumulator accumulator = new ValidationMetadataAccumulator();
		collectValidationMetadata(field, field.getAnnotatedType(), accumulator);
		findGetter(clazz, field.getName())
			.ifPresent(method -> collectValidationMetadata(method, method.getAnnotatedReturnType(), accumulator));
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
		try {
			for (Annotation nestedAnnotation : (Annotation[]) valueMethod.invoke(annotation)) {
				collectPassthroughConstraintAnnotation(nestedAnnotation, passthroughAnnotations);
			}
		}
		catch (IllegalAccessException | InvocationTargetException exception) {
			throw new IllegalStateException(
				"Unable to inspect container constraint annotation: " + annotationType.getName(),
				exception);
		}
	}

	private Method findConstraintContainerValueMethod(Class<? extends Annotation> annotationType) {
		try {
			Method valueMethod = annotationType.getMethod("value");
			Class<?> returnType = valueMethod.getReturnType();
			if (!returnType.isArray() || !Annotation.class.isAssignableFrom(returnType.getComponentType())) {
				return null;
			}
			@SuppressWarnings("unchecked")
			Class<? extends Annotation> nestedAnnotationType =
				(Class<? extends Annotation>) returnType.getComponentType();
			return nestedAnnotationType.isAnnotationPresent(Constraint.class) ? valueMethod : null;
		}
		catch (NoSuchMethodException exception) {
			return null;
		}
	}

	private void validateConstraints(
		String className,
		Field field,
		BaselineFieldConstraints baseline,
		ValidationProperties.Constraints constraints
	) {
		if (constraints == null) {
			constraints = new ValidationProperties.Constraints();
		}

		Class<?> rawFieldType = field.getType();
		Class<?> fieldType = wrapPrimitive(rawFieldType);
		String fieldName = field.getName();
		boolean extensionsConfigured = hasConfiguredExtensions(constraints);

		if (isNotBlankEnabled(constraints) && !supportsCharSequence(fieldType)) {
			throw unsupportedConstraint("notBlank", className, fieldName, fieldType);
		}
		if ((hasNumericBounds(baseline) || hasNumericBounds(constraints)) && !supportsNumericBounds(fieldType)) {
			throw unsupportedConstraint("numeric bounds", className, fieldName, fieldType);
		}
		if (hasSizeBounds(constraints) && !supportsContainerValue(rawFieldType)) {
			throw unsupportedConstraint("size", className, fieldName, rawFieldType);
		}
		if (hasConfiguredPatterns(constraints) && !supportsCharSequence(fieldType)) {
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
			Set<Pattern.Flag> flags = EnumSet.noneOf(Pattern.Flag.class);
			for (Pattern.Flag flag : pattern.flags()) {
				flags.add(flag);
			}
			baselineAccumulator.patterns.add(new PatternRule(pattern.regexp(), flags));
		}
	}

	private Optional<Method> findGetter(Class<?> clazz, String fieldName) {
		String capitalizedFieldName = StringUtils.capitalize(fieldName);
		List<String> getterNames = List.of("get" + capitalizedFieldName, "is" + capitalizedFieldName);

		Class<?> current = clazz;
		while (current != null && current != Object.class) {
			for (String getterName : getterNames) {
				try {
					Method method = current.getDeclaredMethod(getterName);
					if (method.getParameterCount() == 0) {
						return Optional.of(method);
					}
				}
				catch (NoSuchMethodException ignored) {
					// Continue scanning up the class hierarchy.
				}
			}
			current = current.getSuperclass();
		}
		return Optional.empty();
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

	private boolean isNotBlankEnabled(ValidationProperties.Constraints constraints) {
		return Boolean.TRUE.equals(constraints.getNotBlank().getValue())
			|| Boolean.TRUE.equals(constraints.getNotBlank().getHardValue());
	}

	private boolean hasNumericBounds(BaselineFieldConstraints baseline) {
		return baseline.min() != null || baseline.max() != null;
	}

	private boolean hasNumericBounds(ValidationProperties.Constraints constraints) {
		return constraints.getMin().getValue() != null
			|| constraints.getMin().getHardValue() != null
			|| constraints.getMax().getValue() != null
			|| constraints.getMax().getHardValue() != null
			|| constraints.getDecimalMin().getValue() != null
			|| constraints.getDecimalMin().getHardValue() != null
			|| constraints.getDecimalMax().getValue() != null
			|| constraints.getDecimalMax().getHardValue() != null;
	}

	private boolean hasSizeBounds(ValidationProperties.Constraints constraints) {
		return constraints.getSize().getMin().getValue() != null
			|| constraints.getSize().getMin().getHardValue() != null
			|| constraints.getSize().getMax().getValue() != null
			|| constraints.getSize().getMax().getHardValue() != null;
	}

	private boolean hasConfiguredPatterns(ValidationProperties.Constraints constraints) {
		return !constraints.getPattern().getRegexes().isEmpty();
	}

	private boolean hasConfiguredExtensions(ValidationProperties.Constraints constraints) {
		return !constraints.getExtensions().getRules().isEmpty();
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

	private Class<?> wrapPrimitive(Class<?> fieldType) {
		if (!fieldType.isPrimitive()) {
			return fieldType;
		}
		if (fieldType == boolean.class) {
			return Boolean.class;
		}
		if (fieldType == byte.class) {
			return Byte.class;
		}
		if (fieldType == short.class) {
			return Short.class;
		}
		if (fieldType == int.class) {
			return Integer.class;
		}
		if (fieldType == long.class) {
			return Long.class;
		}
		if (fieldType == float.class) {
			return Float.class;
		}
		if (fieldType == double.class) {
			return Double.class;
		}
		if (fieldType == char.class) {
			return Character.class;
		}
		return fieldType;
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

		private BaselineFieldConstraints toBaseline() {
			return new BaselineFieldConstraints(notNull, notBlank, min, max, sizeMin, sizeMax, patterns);
		}
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
				groupConversions.entrySet().stream()
					.map(entry -> new GroupConversionMapping(entry.getKey(), entry.getValue()))
					.toList(),
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
				groupConversions.entrySet().stream()
					.map(entry -> new GroupConversionMapping(entry.getKey(), entry.getValue()))
					.toList());
		}
	}
}
