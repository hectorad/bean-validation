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
		BusinessValidationOverrideProperties properties = new BusinessValidationOverrideProperties();
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
		BusinessValidationOverrideProperties properties = new BusinessValidationOverrideProperties();
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
		BusinessValidationOverrideProperties properties = new BusinessValidationOverrideProperties();
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
		BusinessValidationOverrideProperties properties = new BusinessValidationOverrideProperties();
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
	void shouldEnableNotNullToggleWithMessage() {
		ConstraintOverrideSet constraints = singleFieldConstraints(
			constraint("NotNull", params -> {}, "Field is required"));

		assertThat(constraints.getNotNull().getValue()).isTrue();
		assertThat(constraints.getNotNull().getMessage()).isEqualTo("Field is required");
	}

	@Test
	void shouldEnableNotBlankToggleWithMessage() {
		ConstraintOverrideSet constraints = singleFieldConstraints(
			constraint("NotBlank", params -> {}, "Field must not be blank"));

		assertThat(constraints.getNotBlank().getValue()).isTrue();
		assertThat(constraints.getNotBlank().getMessage()).isEqualTo("Field must not be blank");
	}

	@Test
	void shouldApplyMinConstraintWithValueAndMessage() {
		ConstraintOverrideSet constraints = singleFieldConstraints(
			constraint("Min", params -> params.setValue(BigDecimal.valueOf(18)), "Must be at least 18"));

		assertThat(constraints.getMin().getValue()).isEqualTo(18L);
		assertThat(constraints.getMin().getMessage()).isEqualTo("Must be at least 18");
	}

	@Test
	void shouldApplyMaxConstraintWithValueAndMessage() {
		ConstraintOverrideSet constraints = singleFieldConstraints(
			constraint("Max", params -> params.setValue(BigDecimal.valueOf(150)), "Must be at most 150"));

		assertThat(constraints.getMax().getValue()).isEqualTo(150L);
		assertThat(constraints.getMax().getMessage()).isEqualTo("Must be at most 150");
	}

	@Test
	void shouldApplyDecimalMinWithInclusiveTrue() {
		ConstraintOverrideSet constraints = singleFieldConstraints(
			constraint("DecimalMin", params -> {
				params.setValue(new BigDecimal("9.99"));
				params.setInclusive(true);
			}, "Minimum is 9.99"));

		assertThat(constraints.getDecimalMin().getValue()).isEqualByComparingTo(new BigDecimal("9.99"));
		assertThat(constraints.getDecimalMin().getInclusive()).isTrue();
		assertThat(constraints.getDecimalMin().getMessage()).isEqualTo("Minimum is 9.99");
	}

	@Test
	void shouldApplyDecimalMaxWithInclusiveFalse() {
		ConstraintOverrideSet constraints = singleFieldConstraints(
			constraint("DecimalMax", params -> {
				params.setValue(new BigDecimal("100.00"));
				params.setInclusive(false);
			}, "Must be less than 100"));

		assertThat(constraints.getDecimalMax().getValue()).isEqualByComparingTo(new BigDecimal("100.00"));
		assertThat(constraints.getDecimalMax().getInclusive()).isFalse();
		assertThat(constraints.getDecimalMax().getMessage()).isEqualTo("Must be less than 100");
	}

	@Test
	void shouldStoreNullInclusiveWhenNotSetForDecimalMin() {
		ConstraintOverrideSet constraints = singleFieldConstraints(
			constraint("DecimalMin", params -> params.setValue(new BigDecimal("5.0")), "At least 5"));

		assertThat(constraints.getDecimalMin().getValue()).isEqualByComparingTo(new BigDecimal("5.0"));
		assertThat(constraints.getDecimalMin().getInclusive()).isNull();
		assertThat(constraints.getDecimalMin().getMessage()).isEqualTo("At least 5");
	}

	@Test
	void shouldStoreNullInclusiveWhenNotSetForDecimalMax() {
		ConstraintOverrideSet constraints = singleFieldConstraints(
			constraint("DecimalMax", params -> params.setValue(new BigDecimal("50.0")), "At most 50"));

		assertThat(constraints.getDecimalMax().getValue()).isEqualByComparingTo(new BigDecimal("50.0"));
		assertThat(constraints.getDecimalMax().getInclusive()).isNull();
		assertThat(constraints.getDecimalMax().getMessage()).isEqualTo("At most 50");
	}

	@Test
	void shouldApplyExtensionsConstraintWithJsonPathRegexpAndMessage() {
		ConstraintOverrideSet constraints = singleFieldConstraints(
			constraint("Extensions", params -> {
				params.setJsonPath("$.status");
				params.setRegexp("^(ACTIVE|INACTIVE)$");
			}, "Invalid status"));

		assertThat(constraints.getExtensions().getRules()).singleElement().satisfies(rule -> {
			assertThat(rule.getJsonPath()).isEqualTo("$.status");
			assertThat(rule.getRegex()).isEqualTo("^(ACTIVE|INACTIVE)$");
			assertThat(rule.getMessage()).isEqualTo("Invalid status");
		});
	}

	@Test
	void shouldApplyMultipleDifferentConstraintTypesOnSameField() {
		ConstraintOverrideSet constraints = singleFieldConstraints(
			constraint("NotBlank", params -> {}, "Required"),
			constraint("Size", params -> {
				params.setMin(2L);
				params.setMax(50L);
			}, "Bad length"),
			constraint("Pattern", params -> params.setRegexp("^[A-Za-z]+$"), "Letters only"));

		assertThat(constraints.getNotBlank().getValue()).isTrue();
		assertThat(constraints.getNotBlank().getMessage()).isEqualTo("Required");
		assertThat(constraints.getSize().getMin().getValue()).isEqualTo(2L);
		assertThat(constraints.getSize().getMax().getValue()).isEqualTo(50L);
		assertThat(constraints.getSize().getMin().getMessage()).isEqualTo("Bad length");
		assertThat(constraints.getSize().getMax().getMessage()).isEqualTo("Bad length");
		assertThat(constraints.getPattern().getRules()).singleElement().satisfies(rule -> {
			assertThat(rule.getRegex()).isEqualTo("^[A-Za-z]+$");
			assertThat(rule.getMessage()).isEqualTo("Letters only");
		});
	}

	@Test
	void shouldSkipFieldWithUnknownConstraintType(CapturedOutput output) {
		assertFieldSkippedWithError(output,
			constraint("FooBar"),
			"Unsupported constraintType: FooBar");
	}

	@Test
	void shouldSkipFieldWhenMinValueIsNonIntegerDecimal(CapturedOutput output) {
		assertFieldSkippedWithError(output,
			constraint("Min", params -> params.setValue(new BigDecimal("25.5"))),
			"params.value must be an integer");
	}

	@Test
	void shouldSkipFieldWhenMaxValueIsNonIntegerDecimal(CapturedOutput output) {
		assertFieldSkippedWithError(output,
			constraint("Max", params -> params.setValue(new BigDecimal("25.5"))),
			"params.value must be an integer");
	}

	@Test
	void shouldSkipFieldWhenExtensionsMissingJsonPath(CapturedOutput output) {
		assertFieldSkippedWithError(output,
			constraint("Extensions", params -> params.setRegexp(".*")),
			"Extensions requires params.jsonPath");
	}

	@Test
	void shouldSkipFieldWhenExtensionsMissingRegexp(CapturedOutput output) {
		assertFieldSkippedWithError(output,
			constraint("Extensions", params -> params.setJsonPath("$.foo")),
			"Extensions requires params.regexp");
	}

	@Test
	void shouldSkipFieldWhenSizeHasNeitherMinNorMax(CapturedOutput output) {
		assertFieldSkippedWithError(output,
			constraint("Size", params -> {}),
			"Size requires params.min or params.max");
	}

	@Test
	void shouldApplySharedSizeMessageToMaxOnlyBound() {
		BusinessValidationOverrideProperties properties = new BusinessValidationOverrideProperties();
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

	private ConstraintOverrideSet singleFieldConstraints(
		ValidationProperties.ConstraintMapping... constraints
	) {
		BusinessValidationOverrideProperties properties = new BusinessValidationOverrideProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());
		classMapping.setFields(List.of(field("name", constraints)));
		properties.setBusinessValidationOverride(List.of(classMapping));

		List<ClassValidationOverride> overrides = new PropertiesValidationOverrideContributor(properties).getValidationOverrides();
		return overrides.getFirst().fields().getFirst().constraints();
	}

	private void assertFieldSkippedWithError(
		CapturedOutput output,
		ValidationProperties.ConstraintMapping badConstraint,
		String expectedErrorFragment
	) {
		BusinessValidationOverrideProperties properties = new BusinessValidationOverrideProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());
		classMapping.setFields(List.of(
			field("name", badConstraint),
			field("age", constraint("Min", params -> params.setValue(BigDecimal.valueOf(25))))));
		properties.setBusinessValidationOverride(List.of(classMapping));

		ValidationOverrideRegistry registry = new ValidationOverrideRegistry(
			List.of(new PropertiesValidationOverrideContributor(properties)));

		assertThat(registry.fieldNames(PersonForm.class.getName())).containsExactly("age");
		assertThat(output.getOut())
			.contains("Skipping validation override field mapping from source=properties")
			.contains("class=" + PersonForm.class.getName())
			.contains("field=name")
			.contains(expectedErrorFragment);
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
