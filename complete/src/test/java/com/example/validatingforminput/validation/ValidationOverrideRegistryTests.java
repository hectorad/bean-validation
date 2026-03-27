package com.example.validation.core.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.validation.core.spi.ClassValidationOverride;
import com.example.validation.core.spi.ConstraintOverrideSet;
import com.example.validation.core.spi.FieldValidationOverride;
import com.example.validation.core.spi.ValidationOverrideContributor;
import org.junit.jupiter.api.Test;

import java.util.List;

class ValidationOverrideRegistryTests {

	@Test
	void shouldExposeContributionsForSameTargetInContributorOrder() {
		ValidationOverrideRegistry registry = new ValidationOverrideRegistry(List.of(
			contributor("first", classOverride(SampleTarget.class, fieldOverride("name", constraints -> constraints.getNotBlank().setValue(true)))),
			contributor("second", classOverride(SampleTarget.class, fieldOverride("name", constraints -> constraints.getSize().getMin().setValue(3L))))));

		assertThat(registry.classNames()).containsExactly(SampleTarget.class.getName());
		assertThat(registry.fieldNames(SampleTarget.class.getName())).containsExactly("name");
		assertThat(registry.contributionsFor(SampleTarget.class.getName(), "name"))
			.extracting(RegisteredConstraintOverride::sourceId)
			.containsExactly("first", "second");
	}

	@Test
	void shouldFailWhenDuplicateClassMappingExistsInsideSingleContributor() {
		assertThatThrownBy(() -> new ValidationOverrideRegistry(List.of(contributor(
			"duplicate-class",
			classOverride(SampleTarget.class, fieldOverride("name")),
			classOverride(SampleTarget.class, fieldOverride("age"))))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Duplicate class mapping");
	}

	@Test
	void shouldFailWhenDuplicateFieldMappingExistsInsideSingleContributor() {
		assertThatThrownBy(() -> new ValidationOverrideRegistry(List.of(contributor(
			"duplicate-field",
			classOverride(SampleTarget.class, fieldOverride("name"), fieldOverride("name"))))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Duplicate field mapping");
	}

	private ValidationOverrideContributor contributor(String sourceId, ClassValidationOverride... overrides) {
		return new ValidationOverrideContributor() {
			@Override
			public List<ClassValidationOverride> getValidationOverrides() {
				return List.of(overrides);
			}

			@Override
			public String sourceId() {
				return sourceId;
			}
		};
	}

	private ClassValidationOverride classOverride(Class<?> type, FieldValidationOverride... fields) {
		return new ClassValidationOverride(type.getName(), List.of(fields));
	}

	private FieldValidationOverride fieldOverride(String fieldName) {
		return fieldOverride(fieldName, constraints -> {
		});
	}

	private FieldValidationOverride fieldOverride(
		String fieldName,
		java.util.function.Consumer<ConstraintOverrideSet> customizer
	) {
		ConstraintOverrideSet constraints = new ConstraintOverrideSet();
		customizer.accept(constraints);
		return new FieldValidationOverride(fieldName, constraints);
	}

	private static final class SampleTarget {

		@SuppressWarnings("unused")
		private String name;

		@SuppressWarnings("unused")
		private Integer age;
	}
}
