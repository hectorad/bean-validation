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
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;

@ExtendWith(OutputCaptureExtension.class)
class ConfigDrivenConstraintMappingContributorTests {

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

	@Test
	void shouldRegisterOverrideForInheritedFieldOnSubclass(CapturedOutput output) {
		BusinessValidationOverrideProperties properties = new BusinessValidationOverrideProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(SubclassWithInheritedField.class.getName());

		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName("parentName");
		ValidationProperties.ConstraintMapping constraint = new ValidationProperties.ConstraintMapping();
		constraint.setConstraintType("Size");
		ValidationProperties.ConstraintParameters params = new ValidationProperties.ConstraintParameters();
		params.setMin(5L);
		constraint.setParams(params);
		fieldMapping.setConstraints(List.of(constraint));

		classMapping.setFields(List.of(fieldMapping));
		properties.setBusinessValidationOverride(List.of(classMapping));

		Validator validator = buildValidator(properties);

		assertThat(output.getOut()).doesNotContain("Skipping validation override constraint mapping");

		SubclassWithInheritedField target = new SubclassWithInheritedField();
		target.setParentName("abcd");

		Set<ConstraintViolation<SubclassWithInheritedField>> violations = validator.validate(target);

		assertThat(violations)
			.singleElement()
			.satisfies(violation -> {
				assertThat(violation.getPropertyPath().toString()).isEqualTo("parentName");
				assertThat(violation.getConstraintDescriptor().getAnnotation().annotationType()).isEqualTo(Size.class);
			});
	}

	@Test
	void shouldMergeOverridesWhenSiblingSubclassesConfigureSameInheritedField(CapturedOutput output) {
		BusinessValidationOverrideProperties properties = new BusinessValidationOverrideProperties();

		ValidationProperties.ClassMapping first = new ValidationProperties.ClassMapping();
		first.setFullClassName(FirstSubclassWithInheritedField.class.getName());
		first.setFields(List.of(fieldMappingWithSizeMin("parentName", 3L)));

		ValidationProperties.ClassMapping second = new ValidationProperties.ClassMapping();
		second.setFullClassName(SecondSubclassWithInheritedField.class.getName());
		second.setFields(List.of(fieldMappingWithSizeMin("parentName", 7L)));

		properties.setBusinessValidationOverride(List.of(first, second));

		Validator validator = buildValidator(properties);

		assertThat(output.getOut()).doesNotContain("Skipping validation override constraint mapping");

		FirstSubclassWithInheritedField firstTarget = new FirstSubclassWithInheritedField();
		firstTarget.setParentName("abcde");
		assertThat(validator.validate(firstTarget))
			.as("stricter min=7 should apply to sibling configured with min=3")
			.singleElement()
			.satisfies(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("parentName"));

		SecondSubclassWithInheritedField secondTarget = new SecondSubclassWithInheritedField();
		secondTarget.setParentName("abcdefghij");
		assertThat(validator.validate(secondTarget)).isEmpty();
	}

	private static ValidationProperties.FieldMapping fieldMappingWithSizeMin(String fieldName, long min) {
		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName(fieldName);
		ValidationProperties.ConstraintMapping constraint = new ValidationProperties.ConstraintMapping();
		constraint.setConstraintType("Size");
		ValidationProperties.ConstraintParameters params = new ValidationProperties.ConstraintParameters();
		params.setMin(min);
		constraint.setParams(params);
		fieldMapping.setConstraints(List.of(constraint));
		return fieldMapping;
	}

	private static Validator buildValidator(BusinessValidationOverrideProperties properties) {
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

		ValidatorFactory factory = configuration.buildValidatorFactory();
		return factory.getValidator();
	}

	static class ParentWithField {

		private String parentName;

		public String getParentName() {
			return parentName;
		}

		public void setParentName(String parentName) {
			this.parentName = parentName;
		}
	}

	static class SubclassWithInheritedField extends ParentWithField {
	}

	static class FirstSubclassWithInheritedField extends ParentWithField {
	}

	static class SecondSubclassWithInheritedField extends ParentWithField {
	}
}
