package com.example.validation.core.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.example.validation.core.spi.ClassValidationOverride;
import com.example.validation.core.spi.ConstraintOverrideSet;
import com.example.validatingforminput.PersonForm;

@ExtendWith(OutputCaptureExtension.class)
class PropertiesValidationOverrideContributorTests {

	@Test
	void shouldSkipOnlyFieldWithMalformedConstraintEntryAndKeepOtherFields(CapturedOutput output) {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());
		classMapping.setFields(List.of(
			field("name", constraint("Pattern")),
			field("age", constraint("Min", params -> params.setValue(BigDecimal.valueOf(25))))));
		properties.setBusinessValidationOverride(List.of(classMapping));

		ValidationOverrideRegistry registry = new ValidationOverrideRegistry(
			List.of(new PropertiesValidationOverrideContributor(properties)));

		assertThat(registry.classNames()).containsExactly(PersonForm.class.getName());
		assertThat(registry.fieldNames(PersonForm.class.getName())).containsExactly("age");
		assertThat(output.getOut())
			.contains("Skipping validation override field mapping from source=properties")
			.contains("class=" + PersonForm.class.getName())
			.contains("field=name")
			.contains("constraint[0]")
			.contains("Pattern requires params.regexp");
	}

	@Test
	void shouldPreserveIndependentMessagesForPatternEntries() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());
		classMapping.setFields(List.of(field(
			"name",
			constraint("Pattern", params -> params.setRegexp("^[A-Za-z]+$"), "Letters only"),
			constraint("Pattern", params -> params.setRegexp("^[A-Z].*$"), "Must start uppercase"))));
		properties.setBusinessValidationOverride(List.of(classMapping));

		List<ClassValidationOverride> overrides = new PropertiesValidationOverrideContributor(properties).getValidationOverrides();

		assertThat(overrides).singleElement().satisfies(classOverride ->
			assertThat(classOverride.fields()).singleElement().satisfies(fieldOverride ->
				assertThat(fieldOverride.constraints().getPattern().getRules())
					.extracting(ConstraintOverrideSet.PatternRuleConfig::getMessage)
					.containsExactly("Letters only", "Must start uppercase")));
	}

	@Test
	void shouldApplySharedSizeMessageToBothBounds() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());
		classMapping.setFields(List.of(field(
			"name",
			constraint("Size", params -> {
				params.setMin(3L);
				params.setMax(20L);
			}, "Length is invalid"))));
		properties.setBusinessValidationOverride(List.of(classMapping));

		List<ClassValidationOverride> overrides = new PropertiesValidationOverrideContributor(properties).getValidationOverrides();
		ConstraintOverrideSet constraints = overrides.getFirst().fields().getFirst().constraints();

		assertThat(constraints.getSize().getMin().getValue()).isEqualTo(3L);
		assertThat(constraints.getSize().getMin().getMessage()).isEqualTo("Length is invalid");
		assertThat(constraints.getSize().getMax().getValue()).isEqualTo(20L);
		assertThat(constraints.getSize().getMax().getMessage()).isEqualTo("Length is invalid");
	}

	@Test
	void shouldApplySharedSizeMessageToMinOnlyBound() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());
		classMapping.setFields(List.of(field(
			"name",
			constraint("Size", params -> params.setMin(3L), "Minimum length is invalid"))));
		properties.setBusinessValidationOverride(List.of(classMapping));

		List<ClassValidationOverride> overrides = new PropertiesValidationOverrideContributor(properties).getValidationOverrides();
		ConstraintOverrideSet constraints = overrides.getFirst().fields().getFirst().constraints();

		assertThat(constraints.getSize().getMin().getValue()).isEqualTo(3L);
		assertThat(constraints.getSize().getMin().getMessage()).isEqualTo("Minimum length is invalid");
		assertThat(constraints.getSize().getMax().getValue()).isNull();
		assertThat(constraints.getSize().getMax().getMessage()).isNull();
	}

	@Test
	void shouldApplySharedSizeMessageToMaxOnlyBound() {
		ValidationProperties properties = new ValidationProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());
		classMapping.setFields(List.of(field(
			"name",
			constraint("Size", params -> params.setMax(20L), "Maximum length is invalid"))));
		properties.setBusinessValidationOverride(List.of(classMapping));

		List<ClassValidationOverride> overrides = new PropertiesValidationOverrideContributor(properties).getValidationOverrides();
		ConstraintOverrideSet constraints = overrides.getFirst().fields().getFirst().constraints();

		assertThat(constraints.getSize().getMin().getValue()).isNull();
		assertThat(constraints.getSize().getMin().getMessage()).isNull();
		assertThat(constraints.getSize().getMax().getValue()).isEqualTo(20L);
		assertThat(constraints.getSize().getMax().getMessage()).isEqualTo("Maximum length is invalid");
	}

	private ValidationProperties.FieldMapping field(
		String fieldName,
		ValidationProperties.ConstraintMapping... constraints
	) {
		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName(fieldName);
		fieldMapping.setConstraints(List.of(constraints));
		return fieldMapping;
	}

	private ValidationProperties.ConstraintMapping constraint(String constraintType) {
		return constraint(constraintType, params -> {
		}, null);
	}

	private ValidationProperties.ConstraintMapping constraint(
		String constraintType,
		Consumer<ValidationProperties.ConstraintParameters> customizer
	) {
		return constraint(constraintType, customizer, null);
	}

	private ValidationProperties.ConstraintMapping constraint(
		String constraintType,
		Consumer<ValidationProperties.ConstraintParameters> customizer,
		String message
	) {
		ValidationProperties.ConstraintMapping constraint = new ValidationProperties.ConstraintMapping();
		constraint.setConstraintType(constraintType);
		ValidationProperties.ConstraintParameters params = new ValidationProperties.ConstraintParameters();
		customizer.accept(params);
		constraint.setParams(params);
		constraint.setMessage(message);
		return constraint;
	}
}
