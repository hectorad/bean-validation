package com.example.validatingforminput.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

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

	private static final class UnsupportedConstraintTarget {

		@SuppressWarnings("unused")
		private Boolean active;
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
