package com.example.validation.core.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import ch.qos.logback.classic.Level;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.example.validatingforminput.PersonForm;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@ExtendWith(OutputCaptureExtension.class)
class ConfigDrivenConstraintMappingContributorTests {

	@Test
	void shouldApplySubclassConfiguredOverrideToInheritedField(CapturedOutput output) {
		BusinessValidationOverrideProperties properties = singleClassProperties(
			InheritedFieldTarget.class.getName(),
			fieldWithSizeMin("inheritedCode", 5L, "Inherited code must be at least five characters"));

		try (ValidatorFactory validatorFactory = buildValidatorFactory(properties)) {
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
	void shouldShareDeclaringClassMappingAcrossInheritedFields(CapturedOutput output) {
		BusinessValidationOverrideProperties properties = singleClassProperties(
			InheritedFieldTarget.class.getName(),
			fieldWithSizeMin("inheritedCode", 5L, null),
			fieldWithSizeMin("inheritedLabel", 4L, null));

		try (ValidatorFactory validatorFactory = buildValidatorFactory(properties)) {
			Set<ConstraintViolation<InheritedFieldTarget>> violations =
				validatorFactory.getValidator().validate(new InheritedFieldTarget("abc", "no"));

			assertThat(violations)
				.extracting(violation -> violation.getPropertyPath().toString())
				.containsExactlyInAnyOrder("inheritedCode", "inheritedLabel");
		}
		assertThat(output.getOut()).doesNotContain("Skipping validation override constraint mapping");
	}

	@Test
	void shouldMergeOverridesFromSiblingSubclasses(CapturedOutput output) {
		BusinessValidationOverrideProperties properties = new BusinessValidationOverrideProperties();
		properties.setBusinessValidationOverride(List.of(
			singleFieldClassMapping(
				InheritedFieldTarget.class.getName(),
				fieldWithSizeMin("inheritedCode", 5L, "Target requires five characters")),
			singleFieldClassMapping(
				InheritedFieldSibling.class.getName(),
				fieldWithSizeMin("inheritedCode", 8L, "Sibling requires eight characters"))));

		try (ValidatorFactory validatorFactory = buildValidatorFactory(properties)) {
			Validator validator = validatorFactory.getValidator();

			Set<ConstraintViolation<InheritedFieldTarget>> targetViolations =
				validator.validate(new InheritedFieldTarget("1234567", "ok-label"));
			Set<ConstraintViolation<InheritedFieldSibling>> siblingViolations =
				validator.validate(new InheritedFieldSibling("1234567"));

			assertThat(targetViolations).singleElement().satisfies(violation -> {
				assertThat(violation.getPropertyPath()).hasToString("inheritedCode");
				assertThat(violation.getMessage()).isEqualTo("Sibling requires eight characters");
			});
			assertThat(siblingViolations).singleElement().satisfies(violation -> {
				assertThat(violation.getPropertyPath()).hasToString("inheritedCode");
				assertThat(violation.getMessage()).isEqualTo("Sibling requires eight characters");
			});
		}
		assertThat(output.getOut()).doesNotContain("Skipping validation override constraint mapping");
	}

	@Test
	void shouldKeepDirectFieldOverrideUnchanged(CapturedOutput output) {
		BusinessValidationOverrideProperties properties = singleClassProperties(
			DirectFieldTarget.class.getName(),
			fieldWithSizeMin("code", 5L, "Direct code must be at least five characters"));

		try (ValidatorFactory validatorFactory = buildValidatorFactory(properties)) {
			Set<ConstraintViolation<DirectFieldTarget>> violations =
				validatorFactory.getValidator().validate(new DirectFieldTarget("abc"));

			assertThat(violations).singleElement().satisfies(violation -> {
				assertThat(violation.getPropertyPath()).hasToString("code");
				assertThat(violation.getMessage()).isEqualTo("Direct code must be at least five characters");
			});
		}
		assertThat(output.getOut()).doesNotContain("Skipping validation override constraint mapping");
	}

	@Test
	void shouldReportMergedRuntimeContributionsForSiblingInheritedField(CapturedOutput output) {
		BusinessValidationOverrideProperties properties = new BusinessValidationOverrideProperties();
		properties.setBusinessValidationOverride(List.of(
			singleFieldClassMapping(
				InheritedFieldTarget.class.getName(),
				fieldWithSizeMin("inheritedCode", 5L, "Target requires five characters")),
			singleFieldClassMapping(
				InheritedFieldSibling.class.getName(),
				fieldWithSizeMin("inheritedCode", 8L, "Sibling requires eight characters"))));
		ValidationOverrideRegistry registry = new ValidationOverrideRegistry(
			List.of(new PropertiesValidationOverrideContributor(properties)));
		GeneratedClassMetadataCache metadataCache = new GeneratedClassMetadataCache(registry);
		ValidationTroubleshootingAnalyzer analyzer = new ValidationTroubleshootingAnalyzer(
			registry,
			metadataCache,
			new ConstraintMergeService());

		withTroubleshootingDebugEnabled(analyzer::afterSingletonsInstantiated);

		assertThat(output.getOut())
			.contains("Class: " + InheritedFieldTarget.class.getName())
			.contains("Class: " + InheritedFieldSibling.class.getName())
			.contains("contributors: 2 [properties]")
			.contains("sizeMin:      baseline=none   | configured=8      | effective=8      | message=\"Sibling requires eight characters\"")
			.doesNotContain("contributors: 1 [properties]");
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

	private static BusinessValidationOverrideProperties singleClassProperties(
		String className,
		ValidationProperties.FieldMapping... fieldMappings
	) {
		BusinessValidationOverrideProperties properties = new BusinessValidationOverrideProperties();
		properties.setBusinessValidationOverride(List.of(singleFieldClassMapping(className, fieldMappings)));
		return properties;
	}

	private static ValidationProperties.ClassMapping singleFieldClassMapping(
		String className,
		ValidationProperties.FieldMapping... fieldMappings
	) {
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(className);
		classMapping.setFields(List.of(fieldMappings));
		return classMapping;
	}

	private static ValidationProperties.FieldMapping fieldWithSizeMin(String fieldName, Long min, String message) {
		ValidationProperties.FieldMapping fieldMapping = new ValidationProperties.FieldMapping();
		fieldMapping.setFieldName(fieldName);
		ValidationProperties.ConstraintMapping constraint = new ValidationProperties.ConstraintMapping();
		constraint.setConstraintType("Size");
		ValidationProperties.ConstraintParameters params = new ValidationProperties.ConstraintParameters();
		params.setMin(min);
		constraint.setParams(params);
		if (message != null) {
			constraint.setMessage(message);
		}
		fieldMapping.setConstraints(List.of(constraint));
		return fieldMapping;
	}

	private static ValidatorFactory buildValidatorFactory(BusinessValidationOverrideProperties properties) {
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

		return configuration.buildValidatorFactory();
	}

	private static void withTroubleshootingDebugEnabled(Runnable action) {
		ch.qos.logback.classic.Logger logger =
			(ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ValidationTroubleshootingAnalyzer.class);
		Level previousLevel = logger.getLevel();
		logger.setLevel(Level.DEBUG);
		try {
			action.run();
		}
		finally {
			logger.setLevel(previousLevel);
		}
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

	private static final class InheritedFieldSibling extends InheritedFieldBase {

		InheritedFieldSibling(String inheritedCode) {
			super(inheritedCode, "sibling");
		}
	}

	private static final class DirectFieldTarget {

		@SuppressWarnings("unused")
		private final String code;

		DirectFieldTarget(String code) {
			this.code = code;
		}
	}
}
