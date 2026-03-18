package io.github.hectorad.validation.sample;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import jakarta.validation.ConstraintViolationException;

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {
	"hector.validation.overrides[0].class-name=io.github.hectorad.validation.sample.PersonForm",
	"hector.validation.overrides[0].fields[0].field-name=extensions",
	"hector.validation.overrides[0].fields[0].constraints.extensions.rules[0].json-path=$.vendorExtensionCode",
	"hector.validation.overrides[0].fields[0].constraints.extensions.rules[0].regex=^[A-Z]{3}-[0-9]{4}$"
})
class ApplicationExtensionsValidationTests {

	@Autowired
	private PersonValidationService personValidationService;

	@Test
	void shouldApplyExtensionsJsonPathRegexValidation() {
		PersonForm form = validBaseForm();

		form.setExtensions(Map.of("vendorExtensionCode", "ABC-1234"));
		assertThatCode(() -> personValidationService.validate(form)).doesNotThrowAnyException();

		form.setExtensions(Map.of("vendorExtensionCode", "abc-1234"));
		assertThatThrownBy(() -> personValidationService.validate(form))
			.isInstanceOf(ConstraintViolationException.class);

		form.setExtensions(Map.of("anotherKey", "anything"));
		assertThatCode(() -> personValidationService.validate(form)).doesNotThrowAnyException();
	}

	private PersonForm validBaseForm() {
		PersonForm form = new PersonForm();
		form.setName("Robert");
		form.setAge(30);
		form.setSalary(new BigDecimal("2000.00"));
		return form;
	}
}
