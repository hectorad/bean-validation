package io.github.hectorad.validation.sample;

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
	"hector.validation.overrides[0].class-name=io.github.hectorad.validation.sample.PersonForm",
	"hector.validation.overrides[0].fields[0].field-name=salary"
})
class ApplicationBaselineDecimalValidationTests {

	@Autowired
	private PersonValidationService personValidationService;

	@Test
	void shouldPreserveBaselineDecimalBoundsForConfiguredField() {
		PersonForm form = new PersonForm();
		form.setName("Robert");
		form.setAge(30);
		form.setSalary(new BigDecimal("1000.00"));

		assertThatThrownBy(() -> personValidationService.validate(form))
			.isInstanceOf(ConstraintViolationException.class);

		form.setSalary(new BigDecimal("1000.01"));
		assertThatCode(() -> personValidationService.validate(form)).doesNotThrowAnyException();

		form.setSalary(new BigDecimal("250000.01"));
		assertThatThrownBy(() -> personValidationService.validate(form))
			.isInstanceOf(ConstraintViolationException.class);
	}
}
