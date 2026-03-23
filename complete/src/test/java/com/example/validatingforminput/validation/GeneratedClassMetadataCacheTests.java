package com.example.validatingforminput.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.validatingforminput.PersonForm;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class GeneratedClassMetadataCacheTests {

	@Test
	void shouldFailWhenClassDoesNotExist() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName("com.example.missing.MissingPerson");
		properties.setBusinessValidationOverride(List.of(classMapping));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(properties))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Configured class was not found");
	}

	@Test
	void shouldFailWhenConfiguredFieldDoesNotExist() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());
		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("doesNotExist");
		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(properties))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Configured field was not found");
	}

	@Test
	void shouldFailWhenNotBlankIsConfiguredForNonStringField() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("age");
		fieldMapping.getConstraints().getNotBlank().setValue(true);

		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(properties))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Constraint notBlank is not supported");
	}

	@Test
	void shouldFailWhenSizeIsConfiguredForUnsupportedFieldType() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("age");
		fieldMapping.getConstraints().getSize().getMin().setValue(1L);

		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(properties))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Constraint size is not supported");
	}

	@Test
	void shouldFailWhenPatternIsConfiguredForNonStringField() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("age");
		fieldMapping.getConstraints().getPattern().setRegexes(List.of("^\\d+$"));

		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(properties))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Constraint pattern is not supported");
	}

	@Test
	void shouldFailWhenExtensionsRuleIsConfiguredForUnsupportedFieldType() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(UnsupportedExtensionsFieldTypeTarget.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("extensions");
		ValidationProperties.ExtensionRuleConstraint extensionRule = new ValidationProperties.ExtensionRuleConstraint();
		extensionRule.setJsonPath("$.partner.code");
		extensionRule.setRegex("^[A-Z]+$");
		fieldMapping.getConstraints().getExtensions().setRules(List.of(extensionRule));

		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(properties))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Constraint extensions is not supported");
	}

	@Test
	void shouldAllowExtensionsRuleOnAnySupportedFieldName() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(NonExtensionsMapTarget.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("metadata");
		ValidationProperties.ExtensionRuleConstraint extensionRule = new ValidationProperties.ExtensionRuleConstraint();
		extensionRule.setJsonPath("$.partner.code");
		extensionRule.setRegex("^[A-Z]+$");
		fieldMapping.getConstraints().getExtensions().setRules(List.of(extensionRule));

		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties);

		assertThat(cache.getRequiredResolvedMapping(NonExtensionsMapTarget.class.getName()).fields())
			.singleElement()
			.extracting(ResolvedFieldMapping::fieldName)
			.isEqualTo("metadata");
	}

	@Test
	void shouldFailWhenMinIsConfiguredForUnsupportedFieldType() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(UnsupportedConstraintTarget.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("active");
		fieldMapping.getConstraints().getMin().setValue(1L);

		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(properties))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Constraint numeric bounds is not supported");
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
	void shouldFailWhenDecimalBoundsAreConfiguredForUnsupportedFieldType() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(UnsupportedDecimalConstraintTarget.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("ratio");
		fieldMapping.getConstraints().getDecimalMin().setValue(new java.math.BigDecimal("1.5"));

		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(properties))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Constraint numeric bounds is not supported");
	}

	@Test
	void shouldFailWhenDecimalAnnotationValueIsMalformed() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(MalformedDecimalAnnotationTarget.class.getName());
		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("amount");
		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(properties))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Invalid DecimalMin annotation");
	}

	@Test
	void shouldFailWhenDuplicateClassMappingExists() {
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

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(properties))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Duplicate class mapping");
	}

	@Test
	void shouldFailWhenDuplicateFieldMappingExists() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());

		ValidationProperties.FieldMapping fieldMapping1 = new ValidationProperties.FieldMapping();
		fieldMapping1.setFieldName("name");
		ValidationProperties.FieldMapping fieldMapping2 = new ValidationProperties.FieldMapping();
		fieldMapping2.setFieldName("name");

		classMapping.setFields(List.of(fieldMapping1, fieldMapping2));
		properties.setBusinessValidationOverride(List.of(classMapping));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(properties))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Duplicate field mapping");
	}

	@Test
	void shouldSkipUnknownClassWhenFailOnErrorIsFalse() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName("com.example.missing.MissingPerson");
		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("name");
		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties, false);

		assertThat(cache.getResolvedMappings()).isEmpty();
	}

	@Test
	void shouldSkipUnknownFieldWhenFailOnErrorIsFalse() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());
		ValidationProperties.FieldMapping validField = new ValidationProperties.FieldMapping();
		validField.setFieldName("name");
		ValidationProperties.FieldMapping invalidField = new ValidationProperties.FieldMapping();
		invalidField.setFieldName("doesNotExist");
		classMapping.setFields(List.of(validField, invalidField));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties, false);

		assertThat(cache.getResolvedMappings()).hasSize(1);
		assertThat(cache.getResolvedMappings().get(0).fields())
			.singleElement()
			.extracting(ResolvedFieldMapping::fieldName)
			.isEqualTo("name");
	}

	@Test
	void shouldSkipIncompatibleConstraintWhenFailOnErrorIsFalse() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());

		ValidationProperties.FieldMapping validField = new ValidationProperties.FieldMapping();
		validField.setFieldName("name");

		ValidationProperties.FieldMapping invalidField = new ValidationProperties.FieldMapping();
		invalidField.setFieldName("age");
		invalidField.getConstraints().getNotBlank().setValue(true);

		classMapping.setFields(List.of(validField, invalidField));
		properties.setBusinessValidationOverride(List.of(classMapping));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties, false);

		assertThat(cache.getResolvedMappings()).hasSize(1);
		assertThat(cache.getResolvedMappings().get(0).fields())
			.singleElement()
			.extracting(ResolvedFieldMapping::fieldName)
			.isEqualTo("name");
	}

	@Test
	void shouldSkipDuplicateClassMappingWhenFailOnErrorIsFalse() {
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

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(properties, false);

		assertThat(cache.getResolvedMappings()).hasSize(1);
		assertThat(cache.getResolvedMappings().get(0).fields())
			.singleElement()
			.extracting(ResolvedFieldMapping::fieldName)
			.isEqualTo("name");
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
