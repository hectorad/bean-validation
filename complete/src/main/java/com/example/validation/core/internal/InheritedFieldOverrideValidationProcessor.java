package com.example.validation.core.internal;

import com.example.validation.core.api.ExtensionsJsonPathRegex;
import com.example.validation.core.api.JsonPathRegexRule;
import com.example.validation.core.api.NumericBound;
import com.example.validation.core.api.PatternRule;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

import jakarta.validation.ConstraintTarget;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.validation.Payload;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.ValidateUnwrappedValue;

public class InheritedFieldOverrideValidationProcessor {

	private static final Logger log = LoggerFactory.getLogger(InheritedFieldOverrideValidationProcessor.class);

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

	private static final Class<?>[] EMPTY_GROUPS = new Class<?>[0];

	private static final Class<? extends Payload>[] EMPTY_PAYLOAD = emptyPayloadArray();

	private final List<InheritedFieldOverride> inheritedFieldOverrides;

	public InheritedFieldOverrideValidationProcessor(
		ValidationOverrideRegistry validationOverrideRegistry,
		GeneratedClassMetadataCache generatedClassMetadataCache,
		ConstraintMergeService constraintMergeService
	) {
		this.inheritedFieldOverrides = buildInheritedFieldOverrides(
			validationOverrideRegistry,
			generatedClassMetadataCache,
			constraintMergeService);
	}

	public boolean hasOverrides() {
		return !inheritedFieldOverrides.isEmpty();
	}

	public <T> Set<ConstraintViolation<T>> process(
		T rootBean,
		Set<ConstraintViolation<T>> violations,
		Class<?>... groups
	) {
		if (rootBean == null || violations == null || inheritedFieldOverrides.isEmpty() || !isDefaultGroupValidation(groups)) {
			return violations;
		}

		List<InheritedFieldOverride> applicableOverrides = inheritedFieldOverrides.stream()
			.filter(override -> override.appliesTo(rootBean.getClass()))
			.toList();
		if (applicableOverrides.isEmpty()) {
			return violations;
		}

		Set<ConstraintViolation<T>> processedViolations = new LinkedHashSet<>();
		for (ConstraintViolation<T> violation : violations) {
			if (applicableOverrides.stream().noneMatch(override -> override.replaces(violation))) {
				processedViolations.add(violation);
			}
		}
		for (InheritedFieldOverride inheritedFieldOverride : applicableOverrides) {
			processedViolations.addAll(inheritedFieldOverride.validate(rootBean));
		}
		return processedViolations;
	}

	private List<InheritedFieldOverride> buildInheritedFieldOverrides(
		ValidationOverrideRegistry validationOverrideRegistry,
		GeneratedClassMetadataCache generatedClassMetadataCache,
		ConstraintMergeService constraintMergeService
	) {
		List<InheritedFieldOverride> overrides = new ArrayList<>();
		for (ResolvedClassMapping classMapping : generatedClassMetadataCache.getResolvedMappings()) {
			for (ResolvedFieldMapping fieldMapping : classMapping.fields()) {
				if (!fieldMapping.isInheritedFrom(classMapping.clazz())) {
					continue;
				}
				List<RegisteredConstraintOverride> contributions = validationOverrideRegistry.contributionsFor(
					classMapping.className(),
					fieldMapping.fieldName());
				try {
					EffectiveFieldConstraints effectiveConstraints = constraintMergeService.merge(
						fieldMapping.baselineConstraints(),
						contributions,
						classMapping.className(),
						fieldMapping.fieldName());
					overrides.add(new InheritedFieldOverride(classMapping.clazz(), fieldMapping, effectiveConstraints));
				}
				catch (RuntimeException exception) {
					log.warn(
						"Skipping inherited validation override for class={}, field={}, sources={} due to error: {}",
						classMapping.className(),
						fieldMapping.fieldName(),
						RegisteredConstraintOverride.renderSources(contributions),
						exception.getMessage());
				}
			}
		}
		return List.copyOf(overrides);
	}

	private boolean isDefaultGroupValidation(Class<?>... groups) {
		if (groups == null || groups.length == 0) {
			return true;
		}
		return Arrays.stream(groups).anyMatch(Default.class::equals);
	}

	@SuppressWarnings("unchecked")
	private static Class<? extends Payload>[] emptyPayloadArray() {
		return (Class<? extends Payload>[]) new Class<?>[0];
	}

	private record InheritedFieldOverride(
		Class<?> targetClass,
		ResolvedFieldMapping fieldMapping,
		EffectiveFieldConstraints effectiveConstraints,
		List<CompiledPatternRule> patternRules,
		List<CompiledExtensionRule> extensionRules
	) {

		private InheritedFieldOverride(
			Class<?> targetClass,
			ResolvedFieldMapping fieldMapping,
			EffectiveFieldConstraints effectiveConstraints
		) {
			this(
				targetClass,
				fieldMapping,
				effectiveConstraints,
				compilePatternRules(effectiveConstraints.patterns()),
				compileExtensionRules(effectiveConstraints.extensionRules()));
		}

		private boolean appliesTo(Class<?> runtimeClass) {
			return targetClass.isAssignableFrom(runtimeClass);
		}

		private boolean replaces(ConstraintViolation<?> violation) {
			if (!fieldMapping.fieldName().equals(violation.getPropertyPath().toString())) {
				return false;
			}
			Class<? extends Annotation> annotationType =
				violation.getConstraintDescriptor().getAnnotation().annotationType();
			return MODELED_CONSTRAINT_TYPES.contains(annotationType);
		}

		private <T> Set<ConstraintViolation<T>> validate(T rootBean) {
			Object value = fieldValue(rootBean);
			Set<ConstraintViolation<T>> violations = new LinkedHashSet<>();
			if (effectiveConstraints.notNull() && value == null) {
				violations.add(violation(
					rootBean,
					fieldMapping.fieldName(),
					value,
					NotNull.class,
					effectiveConstraints.notNullMessage(),
					Map.of()));
			}
			if (effectiveConstraints.notBlank() && !isNotBlank(value)) {
				violations.add(violation(
					rootBean,
					fieldMapping.fieldName(),
					value,
					NotBlank.class,
					effectiveConstraints.notBlankMessage(),
					Map.of()));
			}
			if (effectiveConstraints.min() != null && !satisfiesMinimum(value, effectiveConstraints.min())) {
				String boundValue = effectiveConstraints.min().value().toPlainString();
				violations.add(violation(
					rootBean,
					fieldMapping.fieldName(),
					value,
					DecimalMin.class,
					effectiveConstraints.minMessage(),
					Map.of("value", boundValue, "inclusive", effectiveConstraints.min().inclusive())));
			}
			if (effectiveConstraints.max() != null && !satisfiesMaximum(value, effectiveConstraints.max())) {
				String boundValue = effectiveConstraints.max().value().toPlainString();
				violations.add(violation(
					rootBean,
					fieldMapping.fieldName(),
					value,
					DecimalMax.class,
					effectiveConstraints.maxMessage(),
					Map.of("value", boundValue, "inclusive", effectiveConstraints.max().inclusive())));
			}
			if ((effectiveConstraints.sizeMin() != null || effectiveConstraints.sizeMax() != null)
				&& !satisfiesSize(value, effectiveConstraints.sizeMin(), effectiveConstraints.sizeMax())) {
				int min = (effectiveConstraints.sizeMin() == null) ? 0 : effectiveConstraints.sizeMin();
				int max = (effectiveConstraints.sizeMax() == null) ? Integer.MAX_VALUE : effectiveConstraints.sizeMax();
				String message = (effectiveConstraints.sizeMin() == null)
					? effectiveConstraints.sizeMaxMessage()
					: effectiveConstraints.sizeMinMessage();
				violations.add(violation(
					rootBean,
					fieldMapping.fieldName(),
					value,
					Size.class,
					message,
					Map.of("min", min, "max", max)));
			}
			for (CompiledPatternRule patternRule : patternRules) {
				if (!satisfiesPattern(value, patternRule)) {
					violations.add(violation(
						rootBean,
						fieldMapping.fieldName(),
						value,
						Pattern.class,
						patternRule.message(),
						Map.of("regexp", patternRule.regex(), "flags", new Pattern.Flag[0])));
				}
			}
			for (CompiledExtensionRule extensionRule : extensionRules) {
				if (!satisfiesExtensionRule(value, extensionRule)) {
					violations.add(violation(
						rootBean,
						fieldMapping.fieldName(),
						value,
						ExtensionsJsonPathRegex.class,
						extensionRule.message(),
						Map.of("jsonPath", extensionRule.jsonPath(), "regex", extensionRule.regex())));
				}
			}
			return violations;
		}

		private Object fieldValue(Object rootBean) {
			ReflectionUtils.makeAccessible(fieldMapping.field());
			return ReflectionUtils.getField(fieldMapping.field(), rootBean);
		}
	}

	private static List<CompiledPatternRule> compilePatternRules(List<PatternRule> rules) {
		return rules.stream()
			.map(rule -> new CompiledPatternRule(rule.regex(), rule.message(), java.util.regex.Pattern.compile(rule.regex())))
			.toList();
	}

	private static List<CompiledExtensionRule> compileExtensionRules(List<JsonPathRegexRule> rules) {
		return rules.stream()
			.map(rule -> new CompiledExtensionRule(
				rule.jsonPath(),
				rule.regex(),
				rule.message(),
				compileJsonPath(rule.jsonPath()),
				compileRegex(rule.regex())))
			.toList();
	}

	private static JsonPath compileJsonPath(String jsonPath) {
		try {
			return JsonPath.compile(jsonPath);
		}
		catch (InvalidPathException | IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid jsonPath configured for ExtensionsJsonPathRegex: " + jsonPath, exception);
		}
	}

	private static java.util.regex.Pattern compileRegex(String regex) {
		try {
			return java.util.regex.Pattern.compile(regex);
		}
		catch (PatternSyntaxException exception) {
			throw new IllegalArgumentException("Invalid regex configured for ExtensionsJsonPathRegex: " + regex, exception);
		}
	}

	private static boolean isNotBlank(Object value) {
		if (!(value instanceof CharSequence text)) {
			return false;
		}
		return !text.toString().trim().isEmpty();
	}

	private static boolean satisfiesMinimum(Object value, NumericBound bound) {
		if (value == null) {
			return true;
		}
		BigDecimal candidate = toBigDecimal(value);
		if (candidate == null) {
			return false;
		}
		int comparison = candidate.compareTo(bound.value());
		return comparison > 0 || (comparison == 0 && bound.inclusive());
	}

	private static boolean satisfiesMaximum(Object value, NumericBound bound) {
		if (value == null) {
			return true;
		}
		BigDecimal candidate = toBigDecimal(value);
		if (candidate == null) {
			return false;
		}
		int comparison = candidate.compareTo(bound.value());
		return comparison < 0 || (comparison == 0 && bound.inclusive());
	}

	private static BigDecimal toBigDecimal(Object value) {
		try {
			if (value instanceof BigDecimal decimal) {
				return decimal;
			}
			if (value instanceof BigInteger integer) {
				return new BigDecimal(integer);
			}
			if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
				return BigDecimal.valueOf(((Number) value).longValue());
			}
			if (value instanceof CharSequence text) {
				return new BigDecimal(text.toString());
			}
			return null;
		}
		catch (NumberFormatException exception) {
			return null;
		}
	}

	private static boolean satisfiesSize(Object value, Integer min, Integer max) {
		if (value == null) {
			return true;
		}
		Integer size = sizeOf(value);
		if (size == null) {
			return false;
		}
		return (min == null || size >= min) && (max == null || size <= max);
	}

	private static Integer sizeOf(Object value) {
		if (value instanceof CharSequence text) {
			return text.length();
		}
		if (value instanceof Collection<?> collection) {
			return collection.size();
		}
		if (value instanceof Map<?, ?> map) {
			return map.size();
		}
		if (value.getClass().isArray()) {
			return Array.getLength(value);
		}
		return null;
	}

	private static boolean satisfiesPattern(Object value, CompiledPatternRule rule) {
		if (value == null) {
			return true;
		}
		return value instanceof CharSequence text && rule.pattern().matcher(text).matches();
	}

	private static boolean satisfiesExtensionRule(Object value, CompiledExtensionRule rule) {
		if (value == null) {
			return true;
		}
		try {
			Object resolvedValue = readJsonPath(value, rule.compiledJsonPath());
			if (resolvedValue == null) {
				return true;
			}
			List<?> candidates = (resolvedValue instanceof List<?> listValue) ? listValue : List.of(resolvedValue);
			for (Object candidate : candidates) {
				if (!matchesExtensionCandidate(candidate, rule.pattern())) {
					return false;
				}
			}
			return true;
		}
		catch (PathNotFoundException exception) {
			return true;
		}
		catch (com.jayway.jsonpath.JsonPathException exception) {
			return false;
		}
	}

	private static Object readJsonPath(Object value, JsonPath jsonPath) {
		if (value instanceof CharSequence textValue) {
			String raw = textValue.toString();
			if (raw.isBlank()) {
				return null;
			}
			ReadContext context = JsonPath.parse(raw);
			return context.read(jsonPath);
		}
		return jsonPath.read(value);
	}

	private static boolean matchesExtensionCandidate(Object candidate, java.util.regex.Pattern pattern) {
		if (candidate == null) {
			return true;
		}
		if (candidate instanceof CharSequence
			|| candidate instanceof Number
			|| candidate instanceof Boolean
			|| candidate instanceof Character
			|| candidate instanceof Enum<?>) {
			return pattern.matcher(String.valueOf(candidate)).matches();
		}
		return false;
	}

	private static <T, A extends Annotation> ConstraintViolation<T> violation(
		T rootBean,
		String propertyName,
		Object invalidValue,
		Class<A> annotationType,
		String configuredMessage,
		Map<String, Object> constraintAttributes
	) {
		Map<String, Object> attributes = annotationAttributes(annotationType, configuredMessage, constraintAttributes);
		A annotation = annotation(annotationType, attributes);
		String messageTemplate = (String) attributes.get("message");
		String message = interpolate(annotationType, messageTemplate, attributes);
		return new SyntheticConstraintViolation<>(
			message,
			messageTemplate,
			rootBean,
			rootBeanClass(rootBean),
			rootBean,
			new SimplePath(propertyName),
			invalidValue,
			new SyntheticConstraintDescriptor<>(annotation, messageTemplate, attributes));
	}

	private static <A extends Annotation> Map<String, Object> annotationAttributes(
		Class<A> annotationType,
		String configuredMessage,
		Map<String, Object> constraintAttributes
	) {
		Map<String, Object> attributes = new LinkedHashMap<>(constraintAttributes);
		attributes.put("message", (configuredMessage == null) ? defaultMessageTemplate(annotationType) : configuredMessage);
		attributes.put("groups", EMPTY_GROUPS);
		attributes.put("payload", EMPTY_PAYLOAD);
		for (Method method : annotationType.getDeclaredMethods()) {
			if (method.getParameterCount() == 0 && !attributes.containsKey(method.getName())) {
				attributes.put(method.getName(), method.getDefaultValue());
			}
		}
		return attributes;
	}

	private static <A extends Annotation> String defaultMessageTemplate(Class<A> annotationType) {
		try {
			return (String) annotationType.getDeclaredMethod("message").getDefaultValue();
		}
		catch (NoSuchMethodException exception) {
			throw new IllegalStateException("Constraint annotation has no message attribute: " + annotationType.getName(), exception);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> Class<T> rootBeanClass(T rootBean) {
		return (Class<T>) rootBean.getClass();
	}

	@SuppressWarnings("unchecked")
	private static <A extends Annotation> A annotation(Class<A> annotationType, Map<String, Object> attributes) {
		InvocationHandler handler = (proxy, method, args) -> {
			String methodName = method.getName();
			if (method.getDeclaringClass() == Annotation.class && "annotationType".equals(methodName)) {
				return annotationType;
			}
			if (method.getDeclaringClass() == Object.class) {
				return switch (methodName) {
					case "toString" -> annotationToString(annotationType, attributes);
					case "hashCode" -> annotationHashCode(attributes);
					case "equals" -> annotationEquals(annotationType, attributes, args[0]);
					default -> method.invoke(proxy, args);
				};
			}
			if (attributes.containsKey(methodName)) {
				return copyAnnotationValue(attributes.get(methodName));
			}
			return copyAnnotationValue(method.getDefaultValue());
		};
		return (A) Proxy.newProxyInstance(annotationType.getClassLoader(), new Class<?>[] { annotationType }, handler);
	}

	private static Object copyAnnotationValue(Object value) {
		if (value != null && value.getClass().isArray()) {
			int length = Array.getLength(value);
			Object copy = Array.newInstance(value.getClass().getComponentType(), length);
			System.arraycopy(value, 0, copy, 0, length);
			return copy;
		}
		return value;
	}

	private static String annotationToString(Class<? extends Annotation> annotationType, Map<String, Object> attributes) {
		return "@" + annotationType.getName() + attributes;
	}

	private static int annotationHashCode(Map<String, Object> attributes) {
		int result = 0;
		for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
			result += (127 * attribute.getKey().hashCode()) ^ annotationValueHashCode(attribute.getValue());
		}
		return result;
	}

	private static int annotationValueHashCode(Object value) {
		if (value == null) {
			return 0;
		}
		if (!value.getClass().isArray()) {
			return value.hashCode();
		}
		if (value instanceof Object[] objectArray) {
			return Arrays.hashCode(objectArray);
		}
		int length = Array.getLength(value);
		int result = 1;
		for (int index = 0; index < length; index++) {
			result = 31 * result + Objects.hashCode(Array.get(value, index));
		}
		return result;
	}

	private static boolean annotationEquals(
		Class<? extends Annotation> annotationType,
		Map<String, Object> attributes,
		Object other
	) {
		if (!annotationType.isInstance(other)) {
			return false;
		}
		for (Method method : annotationType.getDeclaredMethods()) {
			try {
				if (!annotationValueEquals(attributes.get(method.getName()), method.invoke(other))) {
					return false;
				}
			}
			catch (ReflectiveOperationException exception) {
				return false;
			}
		}
		return true;
	}

	private static boolean annotationValueEquals(Object left, Object right) {
		if (left != null && left.getClass().isArray() && right != null && right.getClass().isArray()) {
			if (left instanceof Object[] leftArray && right instanceof Object[] rightArray) {
				return Arrays.equals(leftArray, rightArray);
			}
			int length = Array.getLength(left);
			if (length != Array.getLength(right)) {
				return false;
			}
			for (int index = 0; index < length; index++) {
				if (!Objects.equals(Array.get(left, index), Array.get(right, index))) {
					return false;
				}
			}
			return true;
		}
		return Objects.equals(left, right);
	}

	private static String interpolate(
		Class<? extends Annotation> annotationType,
		String messageTemplate,
		Map<String, Object> attributes
	) {
		String message = defaultResolvedMessage(annotationType, messageTemplate, attributes);
		for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
			message = message.replace("{" + attribute.getKey() + "}", String.valueOf(attribute.getValue()));
		}
		return message;
	}

	private static String defaultResolvedMessage(
		Class<? extends Annotation> annotationType,
		String messageTemplate,
		Map<String, Object> attributes
	) {
		if (!Objects.equals(messageTemplate, defaultMessageTemplate(annotationType))) {
			return messageTemplate;
		}
		if (annotationType == NotNull.class) {
			return "must not be null";
		}
		if (annotationType == NotBlank.class) {
			return "must not be blank";
		}
		if (annotationType == DecimalMin.class) {
			return Boolean.TRUE.equals(attributes.get("inclusive"))
				? "must be greater than or equal to {value}"
				: "must be greater than {value}";
		}
		if (annotationType == DecimalMax.class) {
			return Boolean.TRUE.equals(attributes.get("inclusive"))
				? "must be less than or equal to {value}"
				: "must be less than {value}";
		}
		if (annotationType == Size.class) {
			return "size must be between {min} and {max}";
		}
		if (annotationType == Pattern.class) {
			return "must match \"{regexp}\"";
		}
		return messageTemplate;
	}

	private record CompiledPatternRule(
		String regex,
		String message,
		java.util.regex.Pattern pattern
	) {
	}

	private record CompiledExtensionRule(
		String jsonPath,
		String regex,
		String message,
		JsonPath compiledJsonPath,
		java.util.regex.Pattern pattern
	) {
	}

	private record SyntheticConstraintViolation<T>(
		String message,
		String messageTemplate,
		T rootBean,
		Class<T> rootBeanClass,
		Object leafBean,
		Path propertyPath,
		Object invalidValue,
		ConstraintDescriptor<?> constraintDescriptor
	) implements ConstraintViolation<T> {

		@Override
		public String getMessage() {
			return message;
		}

		@Override
		public String getMessageTemplate() {
			return messageTemplate;
		}

		@Override
		public T getRootBean() {
			return rootBean;
		}

		@Override
		public Class<T> getRootBeanClass() {
			return rootBeanClass;
		}

		@Override
		public Object getLeafBean() {
			return leafBean;
		}

		@Override
		public Object[] getExecutableParameters() {
			return null;
		}

		@Override
		public Object getExecutableReturnValue() {
			return null;
		}

		@Override
		public Path getPropertyPath() {
			return propertyPath;
		}

		@Override
		public Object getInvalidValue() {
			return invalidValue;
		}

		@Override
		public ConstraintDescriptor<?> getConstraintDescriptor() {
			return constraintDescriptor;
		}

		@Override
		public <U> U unwrap(Class<U> type) {
			if (type.isInstance(this)) {
				return type.cast(this);
			}
			throw new ValidationException("Synthetic constraint violation cannot be unwrapped to " + type.getName());
		}
	}

	private record SyntheticConstraintDescriptor<A extends Annotation>(
		A annotation,
		String messageTemplate,
		Map<String, Object> attributes
	) implements ConstraintDescriptor<A> {

		private SyntheticConstraintDescriptor {
			attributes = Map.copyOf(attributes);
		}

		@Override
		public A getAnnotation() {
			return annotation;
		}

		@Override
		public String getMessageTemplate() {
			return messageTemplate;
		}

		@Override
		public Set<Class<?>> getGroups() {
			return Set.of(Default.class);
		}

		@Override
		public Set<Class<? extends Payload>> getPayload() {
			return Set.of();
		}

		@Override
		public ConstraintTarget getValidationAppliesTo() {
			return null;
		}

		@Override
		public List<Class<? extends jakarta.validation.ConstraintValidator<A, ?>>> getConstraintValidatorClasses() {
			return List.of();
		}

		@Override
		public Map<String, Object> getAttributes() {
			return attributes;
		}

		@Override
		public Set<ConstraintDescriptor<?>> getComposingConstraints() {
			return Set.of();
		}

		@Override
		public boolean isReportAsSingleViolation() {
			return false;
		}

		@Override
		public ValidateUnwrappedValue getValueUnwrapping() {
			return ValidateUnwrappedValue.DEFAULT;
		}

		@Override
		public <U> U unwrap(Class<U> type) {
			if (type.isInstance(this)) {
				return type.cast(this);
			}
			throw new ValidationException("Synthetic constraint descriptor cannot be unwrapped to " + type.getName());
		}
	}

	private record SimplePath(String propertyName) implements Path {

		@Override
		public Iterator<Node> iterator() {
			return List.<Node>of(new SimplePropertyNode(propertyName)).iterator();
		}

		@Override
		public String toString() {
			return propertyName;
		}
	}

	private record SimplePropertyNode(String name) implements Path.PropertyNode {

		@Override
		public String getName() {
			return name;
		}

		@Override
		public boolean isInIterable() {
			return false;
		}

		@Override
		public Integer getIndex() {
			return null;
		}

		@Override
		public Object getKey() {
			return null;
		}

		@Override
		public ElementKind getKind() {
			return ElementKind.PROPERTY;
		}

		@Override
		public <T extends Path.Node> T as(Class<T> nodeType) {
			if (nodeType.isInstance(this)) {
				return nodeType.cast(this);
			}
			throw new ValidationException("Path node cannot be cast to " + nodeType.getName());
		}

		@Override
		public Class<?> getContainerClass() {
			return null;
		}

		@Override
		public Integer getTypeArgumentIndex() {
			return null;
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
