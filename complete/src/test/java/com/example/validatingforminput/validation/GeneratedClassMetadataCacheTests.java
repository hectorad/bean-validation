package com.example.validatingforminput.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.validatingforminput.PersonForm;

class GeneratedClassMetadataCacheTests {

	@Test
	void shouldFailWhenClassDoesNotExist() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setClassName("com.example.missing.MissingPerson");
		properties.setMappings(List.of(classMapping));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(properties))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Configured class was not found");
	}

	@Test
	void shouldFailWhenConfiguredFieldDoesNotExist() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setClassName(PersonForm.class.getName());
		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("doesNotExist");
		classMapping.setFields(List.of(fieldMapping));
		properties.setMappings(List.of(classMapping));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(properties))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Configured field was not found");
	}

	@Test
	void shouldFailWhenNotBlankIsConfiguredForNonStringField() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setClassName(PersonForm.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("age");
		fieldMapping.getConstraints().getNotBlank().setValue(true);

		classMapping.setFields(List.of(fieldMapping));
		properties.setMappings(List.of(classMapping));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(properties))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Constraint notBlank is not supported");
	}

	@Test
	void shouldFailWhenSizeIsConfiguredForUnsupportedFieldType() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setClassName(PersonForm.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("age");
		fieldMapping.getConstraints().getSize().getMin().setValue(1L);

		classMapping.setFields(List.of(fieldMapping));
		properties.setMappings(List.of(classMapping));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(properties))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Constraint size is not supported");
	}

	@Test
	void shouldFailWhenPatternIsConfiguredForNonStringField() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setClassName(PersonForm.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("age");
		fieldMapping.getConstraints().getPattern().setRegexes(List.of("^\\d+$"));

		classMapping.setFields(List.of(fieldMapping));
		properties.setMappings(List.of(classMapping));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(properties))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Constraint pattern is not supported");
	}

	@Test
	void shouldFailWhenMinIsConfiguredForUnsupportedFieldType() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setClassName(UnsupportedConstraintTarget.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("active");
		fieldMapping.getConstraints().getMin().setValue(1L);

		classMapping.setFields(List.of(fieldMapping));
		properties.setMappings(List.of(classMapping));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(properties))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Constraint min/max is not supported");
	}

	private static final class UnsupportedConstraintTarget {

		@SuppressWarnings("unused")
		private Boolean active;
	}
}
