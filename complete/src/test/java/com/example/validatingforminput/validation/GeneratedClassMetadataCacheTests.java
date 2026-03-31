package com.example.validation.core.internal;

import com.example.validation.core.api.*;
import com.example.validation.core.spi.ClassValidationOverride;
import com.example.validation.core.spi.ConstraintOverrideSet;
import com.example.validation.core.spi.FieldValidationOverride;
import com.example.validation.core.spi.ValidationOverrideContributor;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.example.validatingforminput.PersonForm;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@ExtendWith(OutputCaptureExtension.class)
public class GeneratedClassMetadataCacheTests {

	@Test
	void shouldWarnAndSkipWhenClassDoesNotExist(CapturedOutput output) {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName("com.example.missing.MissingPerson");
		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("name");
		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties);

		assertThat(cache.getResolvedMappings()).isEmpty();
		assertThat(output.getOut())
			.contains("Skipping validation override class mapping")
			.contains("class=com.example.missing.MissingPerson")
			.contains("sources=[properties]")
			.contains("Configured class was not found");
	}

	@Test
	void shouldWarnAndSkipWhenConfiguredFieldDoesNotExist(CapturedOutput output) {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());
		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("doesNotExist");
		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties);

		assertThat(cache.getResolvedMappings()).isEmpty();
		assertThat(output.getOut())
			.contains("Skipping validation override field mapping")
			.contains("class=" + PersonForm.class.getName())
			.contains("field=doesNotExist")
			.contains("sources=[properties]")
			.contains("Configured field was not found");
	}

	@Test
	void shouldWarnAndSkipWhenNotBlankIsConfiguredForNonStringField(CapturedOutput output) {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("age");
		fieldMapping.setConstraints(List.of(constraint("NotBlank")));

		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties);

		assertThat(cache.getResolvedMappings()).isEmpty();
		assertThat(output.getOut()).contains("Constraint notBlank is not supported");
	}

	@Test
	void shouldWarnAndSkipWhenSizeIsConfiguredForUnsupportedFieldType(CapturedOutput output) {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("age");
		fieldMapping.setConstraints(List.of(constraint("Size", params -> params.setMin(1L))));

		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties);

		assertThat(cache.getResolvedMappings()).isEmpty();
		assertThat(output.getOut()).contains("Constraint size is not supported");
	}

	@Test
	void shouldWarnAndSkipWhenPatternIsConfiguredForNonStringField(CapturedOutput output) {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("age");
		fieldMapping.setConstraints(List.of(constraint("Pattern", params -> params.setRegexp("^\\d+$"))));

		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties);

		assertThat(cache.getResolvedMappings()).isEmpty();
		assertThat(output.getOut()).contains("Constraint pattern is not supported");
	}

	@Test
	void shouldWarnAndSkipWhenExtensionsRuleIsConfiguredForUnsupportedFieldType(CapturedOutput output) {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(UnsupportedExtensionsFieldTypeTarget.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("extensions");
		fieldMapping.setConstraints(List.of(extensionConstraint("$.partner.code", "^[A-Z]+$")));

		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties);

		assertThat(cache.getResolvedMappings()).isEmpty();
		assertThat(output.getOut()).contains("Constraint extensions is not supported");
	}

	@Test
	void shouldAllowExtensionsRuleOnAnySupportedFieldName() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(NonExtensionsMapTarget.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("metadata");
		fieldMapping.setConstraints(List.of(extensionConstraint("$.partner.code", "^[A-Z]+$")));

		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties);

		assertThat(cache.getRequiredResolvedMapping(NonExtensionsMapTarget.class.getName()).fields())
			.singleElement()
			.extracting(ResolvedFieldMapping::fieldName)
			.isEqualTo("metadata");
	}

	@Test
	void shouldWarnAndSkipWhenMinIsConfiguredForUnsupportedFieldType(CapturedOutput output) {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(UnsupportedConstraintTarget.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("active");
		fieldMapping.setConstraints(List.of(constraint("Min", params -> params.setValue(java.math.BigDecimal.ONE))));

		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties);

		assertThat(cache.getResolvedMappings()).isEmpty();
		assertThat(output.getOut()).contains("Constraint numeric bounds is not supported");
	}

	@Test
	void shouldExtractDecimalBoundsFromAnnotations() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());
		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("salary");
		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties);
		BaselineFieldConstraints baseline = cache.getRequiredResolvedMapping(PersonForm.class.getName()).fields().get(0).baselineConstraints();

		assertThat(baseline.min()).isNotNull();
		assertThat(baseline.min().value()).isEqualByComparingTo("1000.00");
		assertThat(baseline.min().inclusive()).isFalse();
		assertThat(baseline.max()).isNotNull();
		assertThat(baseline.max().value()).isEqualByComparingTo("250000.00");
		assertThat(baseline.max().inclusive()).isTrue();
	}

	@Test
	void shouldResolveInheritedFieldAnnotations() {
		ResolvedFieldMapping resolvedFieldMapping = resolveSingleField(InheritedFieldTarget.class, "nickname");

		assertThat(resolvedFieldMapping.baselineConstraints().notBlank()).isTrue();
	}

	@Test
	void shouldResolveGetterAnnotationsThroughBeanPropertyReadMethod() {
		ResolvedFieldMapping resolvedFieldMapping = resolveSingleField(StandardGetterAnnotatedTarget.class, "code");

		assertThat(resolvedFieldMapping.baselineConstraints().notBlank()).isTrue();
	}

	@Test
	void shouldResolveBooleanIsGetterAnnotationsThroughBeanPropertyReadMethod() {
		ResolvedFieldMapping resolvedFieldMapping = resolveSingleField(BooleanIsGetterAnnotatedTarget.class, "active");

		assertThat(resolvedFieldMapping.validationMetadata().constraintAnnotations())
			.singleElement()
			.extracting(annotation -> annotation.annotationType())
			.isEqualTo(FlagConstraint.class);
	}

	@Test
	void shouldRetainFieldMetadataWhenNoGetterExists() {
		ResolvedFieldMapping resolvedFieldMapping = resolveSingleField(FieldOnlyPassthroughTarget.class, "email");

		assertThat(resolvedFieldMapping.validationMetadata().constraintAnnotations())
			.singleElement()
			.extracting(annotation -> annotation.annotationType())
			.isEqualTo(Email.class);
	}

	@Test
	void shouldExtractNestedPassthroughConstraintFromContainerAnnotationValue() {
		ResolvedFieldMapping resolvedFieldMapping = resolveSingleField(ContainerPassthroughTarget.class, "code");

		assertThat(resolvedFieldMapping.validationMetadata().constraintAnnotations())
			.singleElement()
			.satisfies(annotation -> {
				assertThat(annotation.annotationType()).isEqualTo(AllowedPrefix.class);
				assertThat(((AllowedPrefix) annotation).prefix()).isEqualTo("ID-");
			});
	}

	@Test
	void shouldWarnAndSkipWhenDecimalBoundsAreConfiguredForUnsupportedFieldType(CapturedOutput output) {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(UnsupportedDecimalConstraintTarget.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("ratio");
		fieldMapping.setConstraints(List.of(constraint("DecimalMin", params -> params.setValue(new java.math.BigDecimal("1.5")))));

		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties);

		assertThat(cache.getResolvedMappings()).isEmpty();
		assertThat(output.getOut()).contains("Constraint numeric bounds is not supported");
	}

	@Test
	void shouldWarnAndSkipWhenDecimalAnnotationValueIsMalformed(CapturedOutput output) {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(MalformedDecimalAnnotationTarget.class.getName());
		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("amount");
		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties);

		assertThat(cache.getResolvedMappings()).isEmpty();
		assertThat(output.getOut()).contains("Invalid DecimalMin annotation");
	}

	@Test
	void shouldWarnAndKeepFirstClassMappingWhenDuplicateClassMappingExists(CapturedOutput output) {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping1 = new ValidationProperties.ClassMapping();
		classMapping1.setFullClassName(PersonForm.class.getName());
		ValidationProperties.FieldMapping fieldMapping1 = new ValidationProperties.FieldMapping();
		fieldMapping1.setFieldName("name");
		classMapping1.setFields(List.of(fieldMapping1));

		ValidationProperties.ClassMapping classMapping2 = new ValidationProperties.ClassMapping();
		classMapping2.setFullClassName(PersonForm.class.getName());
		ValidationProperties.FieldMapping fieldMapping2 = new ValidationProperties.FieldMapping();
		fieldMapping2.setFieldName("age");
		classMapping2.setFields(List.of(fieldMapping2));

		properties.setBusinessValidationOverride(List.of(classMapping1, classMapping2));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties);

		assertSingleResolvedField(cache, "name");
		assertThat(output.getOut())
			.contains("Skipping duplicate validation override class mapping")
			.contains("source=properties")
			.contains("class=" + PersonForm.class.getName());
	}

	@Test
	void shouldWarnAndKeepFirstFieldMappingWhenDuplicateFieldMappingExists(CapturedOutput output) {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());

		ValidationProperties.FieldMapping fieldMapping1 = new ValidationProperties.FieldMapping();
		fieldMapping1.setFieldName("name");
		ValidationProperties.FieldMapping fieldMapping2 = new ValidationProperties.FieldMapping();
		fieldMapping2.setFieldName("name");

		classMapping.setFields(List.of(fieldMapping1, fieldMapping2));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties);

		assertSingleResolvedField(cache, "name");
		assertThat(output.getOut())
			.contains("Skipping duplicate validation override field mapping")
			.contains("source=properties")
			.contains("class=" + PersonForm.class.getName())
			.contains("field=name");
	}

	@Test
	void shouldSkipUnknownClassByDefault() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName("com.example.missing.MissingPerson");
		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("name");
		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties);

		assertThat(cache.getResolvedMappings()).isEmpty();
	}

	@Test
	void shouldSkipUnknownFieldAndKeepValidFieldByDefault() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());
		ValidationProperties.FieldMapping validField = new ValidationProperties.FieldMapping();
		validField.setFieldName("name");
		ValidationProperties.FieldMapping invalidField = new ValidationProperties.FieldMapping();
		invalidField.setFieldName("doesNotExist");
		classMapping.setFields(List.of(validField, invalidField));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties);

		assertSingleResolvedField(cache, "name");
	}

	@Test
	void shouldSkipIncompatibleConstraintAndKeepValidFieldByDefault() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());

		ValidationProperties.FieldMapping validField = new ValidationProperties.FieldMapping();
		validField.setFieldName("name");

		ValidationProperties.FieldMapping invalidField = new ValidationProperties.FieldMapping();
		invalidField.setFieldName("age");
		invalidField.setConstraints(List.of(constraint("NotBlank")));

		classMapping.setFields(List.of(validField, invalidField));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties);

		assertSingleResolvedField(cache, "name");
	}

	@Test
	void shouldAllowTargetsIntroducedByCustomContributor() {
		GeneratedClassMetadataCache cache = cache(List.of(
			contributor(classOverride(
				CustomContributorTarget.class,
				fieldOverride("name", constraints -> constraints.getSize().getMin().setValue(5L))))));

		assertThat(cache.getResolvedMappings()).singleElement().satisfies(mapping ->
			assertThat(mapping.className()).isEqualTo(CustomContributorTarget.class.getName()));
	}

	private ResolvedFieldMapping resolveSingleField(Class<?> targetType, String fieldName) {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(targetType.getName());
		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName(fieldName);
		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties);

		return cache.getRequiredResolvedMapping(targetType.getName()).fields().get(0);
	}

	private GeneratedClassMetadataCache cache(List<ValidationOverrideContributor> contributors) {
		return new GeneratedClassMetadataCache(new ValidationOverrideRegistry(contributors));
	}

	private void assertSingleResolvedField(GeneratedClassMetadataCache cache, String expectedFieldName) {
		assertThat(cache.getResolvedMappings()).singleElement().satisfies(classMapping ->
			assertThat(classMapping.fields())
				.singleElement()
				.extracting(ResolvedFieldMapping::fieldName)
				.isEqualTo(expectedFieldName));
	}

	private ValidationOverrideContributor contributor(ClassValidationOverride... overrides) {
		return () -> List.of(overrides);
	}

	private ClassValidationOverride classOverride(Class<?> type, FieldValidationOverride... fields) {
		return new ClassValidationOverride(type.getName(), List.of(fields));
	}

	private FieldValidationOverride fieldOverride(String fieldName, Consumer<ConstraintOverrideSet> customizer) {
		ConstraintOverrideSet constraints = new ConstraintOverrideSet();
		customizer.accept(constraints);
		return new FieldValidationOverride(fieldName, constraints);
	}

	private ValidationProperties.ConstraintMapping constraint(String constraintType) {
		return constraint(constraintType, params -> {
		});
	}

	private ValidationProperties.ConstraintMapping constraint(
		String constraintType,
		Consumer<ValidationProperties.ConstraintParameters> customizer
	) {
		ValidationProperties.ConstraintMapping constraint = new ValidationProperties.ConstraintMapping();
		constraint.setConstraintType(constraintType);
		ValidationProperties.ConstraintParameters params = new ValidationProperties.ConstraintParameters();
		customizer.accept(params);
		constraint.setParams(params);
		return constraint;
	}

	private ValidationProperties.ConstraintMapping extensionConstraint(String jsonPath, String regexp) {
		return constraint("Extensions", params -> {
			params.setJsonPath(jsonPath);
			params.setRegexp(regexp);
		});
	}

	private static final class UnsupportedConstraintTarget {

		@SuppressWarnings("unused")
		private Boolean active;
	}

	private static final class NonExtensionsMapTarget {

		@SuppressWarnings("unused")
		private Map<String, Object> metadata;
	}

	private static final class UnsupportedExtensionsFieldTypeTarget {

		@SuppressWarnings("unused")
		private Integer extensions;
	}

	private static final class UnsupportedDecimalConstraintTarget {

		@SuppressWarnings("unused")
		private Double ratio;
	}

	private static final class CustomContributorTarget {

		@SuppressWarnings("unused")
		private String name;
	}

	private static final class MalformedDecimalAnnotationTarget {

		@SuppressWarnings("unused")
		@DecimalMin("not-a-number")
		private Integer amount;
	}

	private static class InheritedFieldBase {

		@NotBlank
		private String nickname;
	}

	private static final class InheritedFieldTarget extends InheritedFieldBase {
	}

	private static final class StandardGetterAnnotatedTarget {

		@SuppressWarnings("unused")
		private String code;

		@NotBlank
		public String getCode() {
			return code;
		}
	}

	private static final class BooleanIsGetterAnnotatedTarget {

		@SuppressWarnings("unused")
		private boolean active;

		@FlagConstraint
		public boolean isActive() {
			return active;
		}
	}

	private static final class FieldOnlyPassthroughTarget {

		@Email
		private String email;
	}

	private static final class ContainerPassthroughTarget {

		@AllowedPrefixes(@AllowedPrefix(prefix = "ID-"))
		private String code;
	}

	@Documented
	@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE })
	@Retention(RetentionPolicy.RUNTIME)
	@Constraint(validatedBy = AllowedPrefixValidator.class)
	@Repeatable(AllowedPrefixes.class)
	public @interface AllowedPrefix {

		String message() default "must start with configured prefix";

		Class<?>[] groups() default {};

		Class<? extends Payload>[] payload() default {};

		String prefix();
	}

	@Documented
	@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE })
	@Retention(RetentionPolicy.RUNTIME)
	public @interface AllowedPrefixes {

		AllowedPrefix[] value();
	}

	private static final class AllowedPrefixValidator implements ConstraintValidator<AllowedPrefix, String> {

		private String prefix;

		@Override
		public void initialize(AllowedPrefix constraintAnnotation) {
			this.prefix = constraintAnnotation.prefix();
		}

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			return value == null || value.startsWith(prefix);
		}
	}

	@Documented
	@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE })
	@Retention(RetentionPolicy.RUNTIME)
	@Constraint(validatedBy = FlagConstraintValidator.class)
	public @interface FlagConstraint {

		String message() default "flag constraint";

		Class<?>[] groups() default {};

		Class<? extends Payload>[] payload() default {};
	}

	private static final class FlagConstraintValidator implements ConstraintValidator<FlagConstraint, Boolean> {

		@Override
		public boolean isValid(Boolean value, ConstraintValidatorContext context) {
			return true;
		}
	}
}
