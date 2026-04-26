package com.example.validation.core.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.example.validatingforminput.PersonForm;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

@ExtendWith(OutputCaptureExtension.class)
class ConfigDrivenConstraintMappingContributorTests {

	@Test
	void shouldApplySubclassConfiguredOverrideToInheritedField(CapturedOutput output) {
		BusinessValidationOverrideProperties properties = new BusinessValidationOverrideProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(InheritedFieldTarget.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("inheritedCode");
		ValidationProperties.ConstraintMapping constraint = new ValidationProperties.ConstraintMapping();
		constraint.setConstraintType("Size");
		ValidationProperties.ConstraintParameters params = new ValidationProperties.ConstraintParameters();
		params.setMin(5L);
		constraint.setParams(params);
		constraint.setMessage("Inherited code must be at least five characters");
		fieldMapping.setConstraints(List.of(constraint));

		ValidationProperties.FieldMapping secondFieldMapping = new ValidationProperties.FieldMapping();
		secondFieldMapping.setFieldName("inheritedLabel");
		ValidationProperties.ConstraintMapping secondConstraint = new ValidationProperties.ConstraintMapping();
		secondConstraint.setConstraintType("Size");
		ValidationProperties.ConstraintParameters secondParams = new ValidationProperties.ConstraintParameters();
		secondParams.setMin(2L);
		secondConstraint.setParams(secondParams);
		secondFieldMapping.setConstraints(List.of(secondConstraint));

		classMapping.setFields(List.of(fieldMapping, secondFieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		ValidationOverrideRegistry registry = new ValidationOverrideRegistry(
			List.of(new PropertiesValidationOverrideContributor(properties)));
		ConfigDrivenConstraintMappingContributor contributor = new ConfigDrivenConstraintMappingContributor(
			registry,
			new GeneratedClassMetadataCache(registry),
			new ConstraintMergeService());
		HibernateValidatorConfiguration configuration = Validation.byProvider(HibernateValidator.class).configure();

		contributor.createConstraintMappings(() -> {
			var mapping = configuration.createConstraintMapping();
			configuration.addMapping(mapping);
			return mapping;
		});

		try (ValidatorFactory validatorFactory = configuration.buildValidatorFactory()) {
			Set<ConstraintViolation<InheritedFieldTarget>> violations =
				validatorFactory.getValidator().validate(new InheritedFieldTarget("abc", "ok"));

			assertThat(violations).singleElement().satisfies(violation -> {
				assertThat(violation.getPropertyPath()).hasToString("inheritedCode");
				assertThat(violation.getMessage()).isEqualTo("Inherited code must be at least five characters");
			});
		}
		assertThat(output.getOut()).doesNotContain("Skipping validation override constraint mapping");
	}

	@Test
	void shouldWarnAndSkipInvalidConstraintMappings(CapturedOutput output) {
		BusinessValidationOverrideProperties properties = new BusinessValidationOverrideProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(PersonForm.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("name");
		ValidationProperties.ConstraintMapping constraint = new ValidationProperties.ConstraintMapping();
		constraint.setConstraintType("Pattern");
		ValidationProperties.ConstraintParameters params = new ValidationProperties.ConstraintParameters();
		params.setRegexp("[");
		constraint.setParams(params);
		fieldMapping.setConstraints(List.of(constraint));

		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		ValidationOverrideRegistry registry = new ValidationOverrideRegistry(
			List.of(new PropertiesValidationOverrideContributor(properties)));
		ConfigDrivenConstraintMappingContributor contributor = new ConfigDrivenConstraintMappingContributor(
			registry,
			new GeneratedClassMetadataCache(registry),
			new ConstraintMergeService());
		HibernateValidatorConfiguration configuration = Validation.byProvider(HibernateValidator.class).configure();

		contributor.createConstraintMappings(() -> {
			var mapping = configuration.createConstraintMapping();
			configuration.addMapping(mapping);
			return mapping;
		});

		assertThat(output.getOut())
			.contains("Skipping validation override constraint mapping")
			.contains("class=" + PersonForm.class.getName())
			.contains("field=name")
			.contains("regex could not be compiled");
	}

	private static class InheritedFieldBase {

		@SuppressWarnings("unused")
		private final String inheritedCode;

		@SuppressWarnings("unused")
		private final String inheritedLabel;

		InheritedFieldBase(String inheritedCode, String inheritedLabel) {
			this.inheritedCode = inheritedCode;
			this.inheritedLabel = inheritedLabel;
		}
	}

	private static final class InheritedFieldTarget extends InheritedFieldBase {

		InheritedFieldTarget(String inheritedCode, String inheritedLabel) {
			super(inheritedCode, inheritedLabel);
		}
	}
}
