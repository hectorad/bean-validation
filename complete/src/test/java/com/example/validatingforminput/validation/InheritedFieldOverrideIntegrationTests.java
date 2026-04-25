package com.example.validation.core.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.example.validatingforminput.ValidatingFormInputApplication;
import com.example.validatingforminput.perf.PerfMapValidationRequest;
import com.example.validatingforminput.perf.PerfRawValidationRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
	classes = ValidatingFormInputApplication.class,
	properties = {
		"com.ampp.businessValidationOverride[0].fullClassName=com.example.validatingforminput.perf.PerfMapValidationRequest",
		"com.ampp.businessValidationOverride[0].fields[0].fieldName=cartId",
		"com.ampp.businessValidationOverride[0].fields[0].constraints[0].constraintType=Size",
		"com.ampp.businessValidationOverride[0].fields[0].constraints[0].params.min=12",
		"com.ampp.businessValidationOverride[0].fields[0].constraints[0].message=Cart id must be at least 12 characters",
		"com.ampp.businessValidationOverride[1].fullClassName=com.example.validation.core.internal.InheritedFieldOverrideIntegrationTests$DuplicateSubclass",
		"com.ampp.businessValidationOverride[1].fields[0].fieldName=code",
		"com.ampp.businessValidationOverride[1].fields[0].constraints[0].constraintType=Size",
		"com.ampp.businessValidationOverride[1].fields[0].constraints[0].params.min=5",
		"com.ampp.businessValidationOverride[1].fields[0].constraints[0].message=Configured code must be at least 5 characters"
	})
class InheritedFieldOverrideIntegrationTests {

	@Autowired
	private Validator validator;

	@Test
	void shouldEnforceSubclassOverrideOnInheritedPerfMapCartId() {
		PerfMapValidationRequest request = perfMapRequest("CART-ABCD");

		Set<ConstraintViolation<PerfMapValidationRequest>> violations = validator.validate(request);

		assertThat(violations).singleElement().satisfies(violation -> {
			assertThat(violation.getPropertyPath().toString()).isEqualTo("cartId");
			assertThat(violation.getMessage()).isEqualTo("Cart id must be at least 12 characters");
			assertThat(violation.getConstraintDescriptor().getAnnotation().annotationType()).isEqualTo(Size.class);
		});
	}

	@Test
	void shouldNotApplySubclassOverrideToSiblingPerfRawCartId() {
		PerfRawValidationRequest request = perfRawRequest("CART-ABCD");

		Set<ConstraintViolation<PerfRawValidationRequest>> violations = validator.validate(request);

		assertThat(violations).isEmpty();
	}

	@Test
	void shouldReplaceBaselineModeledViolationWithSingleConfiguredInheritedViolation() {
		DuplicateSubclass target = new DuplicateSubclass();
		target.setCode("ab");

		Set<ConstraintViolation<DuplicateSubclass>> violations = validator.validate(target);

		assertThat(violations).singleElement().satisfies(violation -> {
			assertThat(violation.getPropertyPath().toString()).isEqualTo("code");
			assertThat(violation.getMessage()).isEqualTo("Configured code must be at least 5 characters");
			assertThat(violation.getConstraintDescriptor().getAnnotation().annotationType()).isEqualTo(Size.class);
		});
	}

	private PerfMapValidationRequest perfMapRequest(String cartId) {
		PerfMapValidationRequest request = new PerfMapValidationRequest();
		setValidBaseFields(request, cartId);
		return request;
	}

	private PerfRawValidationRequest perfRawRequest(String cartId) {
		PerfRawValidationRequest request = new PerfRawValidationRequest();
		setValidBaseFields(request, cartId);
		return request;
	}

	private void setValidBaseFields(PerfMapValidationRequest request, String cartId) {
		request.setCartId(cartId);
		request.setCustomerId("CUSTOMER-123");
		request.setCurrency("USD");
		request.setTotalAmount(new BigDecimal("1250.00"));
	}

	private void setValidBaseFields(PerfRawValidationRequest request, String cartId) {
		request.setCartId(cartId);
		request.setCustomerId("CUSTOMER-123");
		request.setCurrency("USD");
		request.setTotalAmount(new BigDecimal("1250.00"));
	}

	static class DuplicateBase {

		@Size(min = 3, message = "Baseline code must be at least 3 characters")
		private String code;

		public String getCode() {
			return code;
		}

		public void setCode(String code) {
			this.code = code;
		}
	}

	static class DuplicateSubclass extends DuplicateBase {
	}
}
