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
	"hector.validation.overrides[0].fields[0].field-name=salary",
	"hector.validation.overrides[0].fields[0].constraints.decimal-min.value=5000.50",
	"hector.validation.overrides[0].fields[0].constraints.decimal-min.inclusive=false",
	"hector.validation.overrides[0].fields[0].constraints.decimal-max.value=7500.75",
	"hector.validation.overrides[0].fields[0].constraints.decimal-max.inclusive=true"
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
