package com.example.validatingforminput.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.validatingforminput.PersonForm;

import jakarta.validation.constraints.DecimalMin;

class GeneratedClassMetadataCacheTests {

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
}
