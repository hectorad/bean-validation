package com.example.validatingforminput.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.validatingforminput.PersonForm;

import jakarta.validation.constraints.DecimalMin;

class GeneratedClassMetadataCacheTests {

	@Test
	void shouldFailWhenClassDoesNotExist() {
		List<ClassValidationOverride> overrides = List.of(
			new ClassValidationOverride("com.example.missing.MissingPerson", List.of()));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(overrides))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Configured class was not found");
	}

	@Test
	void shouldFailWhenConfiguredFieldDoesNotExist() {
		List<ClassValidationOverride> overrides = List.of(
			new ClassValidationOverride(PersonForm.class.getName(), List.of(
				new FieldValidationOverride("doesNotExist", ConstraintOverrideSet.EMPTY))));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(overrides))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Configured field was not found");
	}

	@Test
	void shouldFailWhenNotBlankIsConfiguredForNonStringField() {
		List<ClassValidationOverride> overrides = List.of(
			new ClassValidationOverride(PersonForm.class.getName(), List.of(
				new FieldValidationOverride("age", new ConstraintOverrideSet(
					null, new ConstraintOverrideSet.BooleanOverride(true, null),
					null, null, null, null, null, null, null)))));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(overrides))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Constraint notBlank is not supported");
	}

	@Test
	void shouldFailWhenSizeIsConfiguredForUnsupportedFieldType() {
		List<ClassValidationOverride> overrides = List.of(
			new ClassValidationOverride(PersonForm.class.getName(), List.of(
				new FieldValidationOverride("age", new ConstraintOverrideSet(
					null, null, null, null, null, null,
					new ConstraintOverrideSet.SizeOverride(
						new ConstraintOverrideSet.NumericOverride(1L, null), null),
					null, null)))));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(overrides))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Constraint size is not supported");
	}

	@Test
	void shouldFailWhenPatternIsConfiguredForNonStringField() {
		List<ClassValidationOverride> overrides = List.of(
			new ClassValidationOverride(PersonForm.class.getName(), List.of(
				new FieldValidationOverride("age", new ConstraintOverrideSet(
					null, null, null, null, null, null, null,
					new ConstraintOverrideSet.PatternOverride(List.of("^\\d+$"), List.of(), null),
					null)))));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(overrides))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Constraint pattern is not supported");
	}

	@Test
	void shouldFailWhenExtensionsRuleIsConfiguredForUnsupportedFieldType() {
		List<ClassValidationOverride> overrides = List.of(
			new ClassValidationOverride(UnsupportedExtensionsFieldTypeTarget.class.getName(), List.of(
				new FieldValidationOverride("extensions", new ConstraintOverrideSet(
					null, null, null, null, null, null, null, null,
					new ConstraintOverrideSet.ExtensionsOverride(List.of(
						new ConstraintOverrideSet.ExtensionRuleOverride("$.partner.code", "^[A-Z]+$", null))))))));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(overrides))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Constraint extensions is not supported");
	}

	@Test
	void shouldAllowExtensionsRuleOnAnySupportedFieldName() {
		List<ClassValidationOverride> overrides = List.of(
			new ClassValidationOverride(NonExtensionsMapTarget.class.getName(), List.of(
				new FieldValidationOverride("metadata", new ConstraintOverrideSet(
					null, null, null, null, null, null, null, null,
					new ConstraintOverrideSet.ExtensionsOverride(List.of(
						new ConstraintOverrideSet.ExtensionRuleOverride("$.partner.code", "^[A-Z]+$", null))))))));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(overrides);

		assertThat(cache.getRequiredResolvedMapping(NonExtensionsMapTarget.class.getName()).fields())
			.singleElement()
			.extracting(ResolvedFieldMapping::fieldName)
			.isEqualTo("metadata");
	}

	@Test
	void shouldFailWhenMinIsConfiguredForUnsupportedFieldType() {
		List<ClassValidationOverride> overrides = List.of(
			new ClassValidationOverride(UnsupportedConstraintTarget.class.getName(), List.of(
				new FieldValidationOverride("active", new ConstraintOverrideSet(
					null, null, new ConstraintOverrideSet.NumericOverride(1L, null),
					null, null, null, null, null, null)))));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(overrides))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Constraint numeric bounds is not supported");
	}

	@Test
	void shouldExtractDecimalBoundsFromAnnotations() {
		List<ClassValidationOverride> overrides = List.of(
			new ClassValidationOverride(PersonForm.class.getName(), List.of(
				new FieldValidationOverride("salary", ConstraintOverrideSet.EMPTY))));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(overrides);
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
		List<ClassValidationOverride> overrides = List.of(
			new ClassValidationOverride(UnsupportedDecimalConstraintTarget.class.getName(), List.of(
				new FieldValidationOverride("ratio", new ConstraintOverrideSet(
					null, null, null, null,
					new ConstraintOverrideSet.DecimalOverride(new BigDecimal("1.5"), null, null),
					null, null, null, null)))));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(overrides))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Constraint numeric bounds is not supported");
	}

	@Test
	void shouldFailWhenDecimalAnnotationValueIsMalformed() {
		List<ClassValidationOverride> overrides = List.of(
			new ClassValidationOverride(MalformedDecimalAnnotationTarget.class.getName(), List.of(
				new FieldValidationOverride("amount", ConstraintOverrideSet.EMPTY))));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(overrides))
			.isInstanceOf(InvalidConstraintConfigurationException.class)
			.hasMessageContaining("Invalid DecimalMin annotation");
	}

	@Test
	void shouldFailWhenDuplicateClassMappingExists() {
		List<ClassValidationOverride> overrides = List.of(
			new ClassValidationOverride(PersonForm.class.getName(), List.of(
				new FieldValidationOverride("name", ConstraintOverrideSet.EMPTY))),
			new ClassValidationOverride(PersonForm.class.getName(), List.of(
				new FieldValidationOverride("age", ConstraintOverrideSet.EMPTY))));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(overrides))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Duplicate class mapping");
	}

	@Test
	void shouldFailWhenDuplicateFieldMappingExists() {
		List<ClassValidationOverride> overrides = List.of(
			new ClassValidationOverride(PersonForm.class.getName(), List.of(
				new FieldValidationOverride("name", ConstraintOverrideSet.EMPTY),
				new FieldValidationOverride("name", ConstraintOverrideSet.EMPTY))));

		assertThatThrownBy(() -> new GeneratedClassMetadataCache(overrides))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Duplicate field mapping");
	}

	@Test
	void shouldSkipUnknownClassWhenFailOnErrorIsFalse() {
		List<ClassValidationOverride> overrides = List.of(
			new ClassValidationOverride("com.example.missing.MissingPerson", List.of(
				new FieldValidationOverride("name", ConstraintOverrideSet.EMPTY))));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(overrides, false);

		assertThat(cache.getResolvedMappings()).isEmpty();
	}

	@Test
	void shouldSkipUnknownFieldWhenFailOnErrorIsFalse() {
		List<ClassValidationOverride> overrides = List.of(
			new ClassValidationOverride(PersonForm.class.getName(), List.of(
				new FieldValidationOverride("name", ConstraintOverrideSet.EMPTY),
				new FieldValidationOverride("doesNotExist", ConstraintOverrideSet.EMPTY))));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(overrides, false);

		assertThat(cache.getResolvedMappings()).hasSize(1);
		assertThat(cache.getResolvedMappings().get(0).fields())
			.singleElement()
			.extracting(ResolvedFieldMapping::fieldName)
			.isEqualTo("name");
	}

	@Test
	void shouldSkipIncompatibleConstraintWhenFailOnErrorIsFalse() {
		List<ClassValidationOverride> overrides = List.of(
			new ClassValidationOverride(PersonForm.class.getName(), List.of(
				new FieldValidationOverride("name", ConstraintOverrideSet.EMPTY),
				new FieldValidationOverride("age", new ConstraintOverrideSet(
					null, new ConstraintOverrideSet.BooleanOverride(true, null),
					null, null, null, null, null, null, null)))));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(overrides, false);

		assertThat(cache.getResolvedMappings()).hasSize(1);
		assertThat(cache.getResolvedMappings().get(0).fields())
			.singleElement()
			.extracting(ResolvedFieldMapping::fieldName)
			.isEqualTo("name");
	}

	@Test
	void shouldSkipDuplicateClassMappingWhenFailOnErrorIsFalse() {
		List<ClassValidationOverride> overrides = List.of(
			new ClassValidationOverride(PersonForm.class.getName(), List.of(
				new FieldValidationOverride("name", ConstraintOverrideSet.EMPTY))),
			new ClassValidationOverride(PersonForm.class.getName(), List.of(
				new FieldValidationOverride("age", ConstraintOverrideSet.EMPTY))));

		GeneratedClassMetadataCache cache = new GeneratedClassMetadataCache(overrides, false);

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
