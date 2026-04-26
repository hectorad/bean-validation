package com.example.validation.core.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.example.validatingforminput.perf.PerfMapValidationRequest;
import com.example.validatingforminput.perf.PerfRawValidationRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@ExtendWith(OutputCaptureExtension.class)
class InheritedFieldOverrideTests {

	private static final String OVERRIDE_PATTERN = "^CART-X[A-Z0-9]{3,18}$";

	private static final String OVERRIDE_MESSAGE = "cartId must start with CART-X";

	@Test
	void shouldEnforceOverrideOnInheritedFieldWhenConfiguredOnSubclass() {
		Validator validator = buildValidator(properties(PerfMapValidationRequest.class.getName()));

		PerfMapValidationRequest request = new PerfMapValidationRequest();
		request.setCartId("CART-AB12");
		request.setCustomerId("CUST-1234");
		request.setCurrency("USD");
		request.setTotalAmount(new BigDecimal("1500.00"));

		Set<ConstraintViolation<PerfMapValidationRequest>> violations = validator.validate(request);

		assertThat(violations)
			.extracting(ConstraintViolation::getPropertyPath)
			.extracting(Object::toString)
			.containsExactly("cartId");
		assertThat(violations)
			.extracting(ConstraintViolation::getMessage)
			.containsExactly(OVERRIDE_MESSAGE);
	}

	@Test
	void shouldNotLeakInheritedFieldOverrideToSiblingSubclass() {
		Validator validator = buildValidator(properties(PerfMapValidationRequest.class.getName()));

		PerfRawValidationRequest sibling = new PerfRawValidationRequest();
		sibling.setCartId("CART-AB12");
		sibling.setCustomerId("CUST-1234");
		sibling.setCurrency("USD");
		sibling.setTotalAmount(new BigDecimal("1500.00"));

		Set<ConstraintViolation<PerfRawValidationRequest>> violations = validator.validate(sibling);

		assertThat(violations).isEmpty();
	}

	@Test
	void shouldWarnAndSkipWhenInheritedFieldHasNoGetter(CapturedOutput output) {
		BusinessValidationOverrideProperties properties = new BusinessValidationOverrideProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(NoGetterChild.class.getName());
		classMapping.setFields(List.of(field(
			"opaque",
			constraint("NotBlank", params -> { }, "must not be blank"))));
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
			.contains("class=" + NoGetterChild.class.getName())
			.contains("field=opaque")
			.contains("inherited field with no accessible getter");
	}

	private Validator buildValidator(BusinessValidationOverrideProperties properties) {
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

	private BusinessValidationOverrideProperties properties(String className) {
		BusinessValidationOverrideProperties properties = new BusinessValidationOverrideProperties();
		ValidationProperties.ClassMapping classMapping = new ValidationProperties.ClassMapping();
		classMapping.setFullClassName(className);
		classMapping.setFields(List.of(field("cartId",
			constraint("Pattern", params -> params.setRegexp(OVERRIDE_PATTERN), OVERRIDE_MESSAGE))));
		properties.setBusinessValidationOverride(List.of(classMapping));
		return properties;
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

	static class NoGetterParent {

		String opaque;
	}

	static class NoGetterChild extends NoGetterParent {
	}
}
