package com.example.validatingforminput.validation;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Component
public class GeneratedClassMetadataCache {

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

		for (ValidationProperties.ClassMapping classMapping : validationProperties.getMappings()) {
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

				Field field = resolveField(clazz, className, fieldName);
				BaselineFieldConstraints baseline = extractBaseline(clazz, field);
				fieldMappingIndex.put(fieldName, new ResolvedFieldMapping(fieldName, baseline));
			}

			mappingIndex.put(className,
				new ResolvedClassMapping(className, clazz, new ArrayList<>(fieldMappingIndex.values())));
		}

		return Collections.unmodifiableMap(mappingIndex);
	}

	private String normalizeClassName(ValidationProperties.ClassMapping classMapping) {
		if (classMapping == null || !StringUtils.hasText(classMapping.getClassName())) {
			throw new IllegalStateException("Each validation class mapping must define a non-empty className.");
		}
		return classMapping.getClassName().trim();
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
				Field declaredField = current.getDeclaredField(fieldName);
				declaredField.setAccessible(true);
				return declaredField;
			}
			catch (NoSuchFieldException ignored) {
				current = current.getSuperclass();
			}
		}
		throw new IllegalStateException(
			"Configured field was not found. class=" + className + ", field=" + fieldName);
	}

	private BaselineFieldConstraints extractBaseline(Class<?> clazz, Field field) {
		BaselineAccumulator baselineAccumulator = new BaselineAccumulator();
		collectAnnotationBaseline(field, baselineAccumulator);
		findGetter(clazz, field.getName()).ifPresent(method -> collectAnnotationBaseline(method, baselineAccumulator));
		return baselineAccumulator.toBaseline();
	}

	private void collectAnnotationBaseline(AnnotatedElement element, BaselineAccumulator baselineAccumulator) {
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
			baselineAccumulator.min = maxNullable(baselineAccumulator.min, min.value());
		}
		for (Max max : element.getAnnotationsByType(Max.class)) {
			baselineAccumulator.max = minNullable(baselineAccumulator.max, max.value());
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

	private java.util.Optional<Method> findGetter(Class<?> clazz, String fieldName) {
		String capitalizedFieldName = StringUtils.capitalize(fieldName);
		List<String> getterNames = List.of("get" + capitalizedFieldName, "is" + capitalizedFieldName);

		Class<?> current = clazz;
		while (current != null && current != Object.class) {
			for (String getterName : getterNames) {
				try {
					Method method = current.getDeclaredMethod(getterName);
					if (method.getParameterCount() == 0) {
						method.setAccessible(true);
						return java.util.Optional.of(method);
					}
				}
				catch (NoSuchMethodException ignored) {
					// Continue scanning up the class hierarchy.
				}
			}
			current = current.getSuperclass();
		}
		return java.util.Optional.empty();
	}

	private Long maxNullable(Long first, long second) {
		return (first == null) ? second : Math.max(first, second);
	}

	private Long minNullable(Long first, long second) {
		return (first == null) ? second : Math.min(first, second);
	}

	private Integer maxNullable(Integer first, int second) {
		return (first == null) ? second : Math.max(first, second);
	}

	private Integer minNullable(Integer first, int second) {
		return (first == null) ? second : Math.min(first, second);
	}

	private static final class BaselineAccumulator {

		private boolean notNull;

		private boolean notBlank;

		private Long min;

		private Long max;

		private Integer sizeMin;

		private Integer sizeMax;

		private final List<PatternRule> patterns = new ArrayList<>();

		private BaselineFieldConstraints toBaseline() {
			return new BaselineFieldConstraints(notNull, notBlank, min, max, sizeMin, sizeMax, patterns);
		}
	}
}
