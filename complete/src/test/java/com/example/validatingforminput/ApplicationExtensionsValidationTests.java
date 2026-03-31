package com.example.validatingforminput;

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
	"com.ampp.businessValidationOverride[0].fullClassName=com.example.validatingforminput.PersonForm",
	"com.ampp.businessValidationOverride[0].fields[0].fieldName=extensions",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[0].constraintType=Extensions",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[0].params.jsonPath=$.vendorExtensionCode",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[0].params.regexp=^[A-Z]{3}-[0-9]{4}$"
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
