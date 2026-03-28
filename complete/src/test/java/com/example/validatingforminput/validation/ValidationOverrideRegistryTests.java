package com.example.validation.core.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.validation.core.spi.ClassValidationOverride;
import com.example.validation.core.spi.ConstraintOverrideSet;
import com.example.validation.core.spi.FieldValidationOverride;
import com.example.validation.core.spi.ValidationOverrideContributor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

@ExtendWith(OutputCaptureExtension.class)
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
	void shouldWarnAndKeepFirstClassMappingWhenDuplicateClassMappingExistsInsideSingleContributor(CapturedOutput output) {
		ValidationOverrideRegistry registry = new ValidationOverrideRegistry(List.of(contributor(
			"duplicate-class",
			classOverride(SampleTarget.class, fieldOverride("name")),
			classOverride(SampleTarget.class, fieldOverride("age")))));

		assertThat(registry.classNames()).containsExactly(SampleTarget.class.getName());
		assertThat(registry.fieldNames(SampleTarget.class.getName())).containsExactly("name");
		assertThat(output.getOut())
			.contains("Skipping duplicate validation override class mapping")
			.contains("source=duplicate-class")
			.contains("class=" + SampleTarget.class.getName());
	}

	@Test
	void shouldWarnAndKeepFirstFieldMappingWhenDuplicateFieldMappingExistsInsideSingleContributor(CapturedOutput output) {
		ValidationOverrideRegistry registry = new ValidationOverrideRegistry(List.of(contributor(
			"duplicate-field",
			classOverride(SampleTarget.class, fieldOverride("name"), fieldOverride("name")))));

		assertThat(registry.classNames()).containsExactly(SampleTarget.class.getName());
		assertThat(registry.fieldNames(SampleTarget.class.getName())).containsExactly("name");
		assertThat(registry.contributionsFor(SampleTarget.class.getName(), "name")).singleElement();
		assertThat(output.getOut())
			.contains("Skipping duplicate validation override field mapping")
			.contains("source=duplicate-field")
			.contains("class=" + SampleTarget.class.getName())
			.contains("field=name");
	}

	@Test
	void shouldWarnAndSkipMalformedMappingsInsideContributor(CapturedOutput output) {
		ValidationOverrideRegistry registry = new ValidationOverrideRegistry(List.of(contributor(
			"malformed",
			new ClassValidationOverride(" ", List.of(fieldOverride("name"))),
			classOverride(
				SampleTarget.class,
				new FieldValidationOverride(" ", new ConstraintOverrideSet()),
				fieldOverride("age")))));

		assertThat(registry.classNames()).containsExactly(SampleTarget.class.getName());
		assertThat(registry.fieldNames(SampleTarget.class.getName())).containsExactly("age");
		assertThat(output.getOut())
			.contains("Skipping validation override class mapping from source=malformed because className is missing.")
			.contains("Skipping validation override field mapping from source=malformed for class="
				+ SampleTarget.class.getName() + " because fieldName is missing.");
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
