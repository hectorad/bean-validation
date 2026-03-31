package com.example.validatingforminput;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import jakarta.validation.ConstraintViolationException;

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {
	"com.ampp.businessValidationOverride[0].fullClassName=com.example.validatingforminput.PersonForm",
	"com.ampp.businessValidationOverride[0].fields[0].fieldName=salary",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[0].constraintType=DecimalMin",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[0].params.value=5000.50",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[0].params.inclusive=false",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[1].constraintType=DecimalMax",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[1].params.value=7500.75",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[1].params.inclusive=true"
})
class ApplicationConfiguredDecimalValidationTests {

	@Autowired
	private PersonValidationService personValidationService;

	@Test
	void shouldApplyConfiguredDecimalBounds() {
		PersonForm form = new PersonForm();
		form.setName("Robert");
		form.setAge(30);
		form.setSalary(new BigDecimal("5000.50"));

		assertThatThrownBy(() -> personValidationService.validate(form))
			.isInstanceOf(ConstraintViolationException.class);

		form.setSalary(new BigDecimal("5000.51"));
		assertThatCode(() -> personValidationService.validate(form)).doesNotThrowAnyException();

		form.setSalary(new BigDecimal("7500.76"));
		assertThatThrownBy(() -> personValidationService.validate(form))
			.isInstanceOf(ConstraintViolationException.class);
	}
}
