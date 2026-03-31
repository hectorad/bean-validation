package com.example.validatingforminput;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.validation.ConstraintViolationException;

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {
	"com.ampp.businessValidationOverride[0].fullClassName=com.example.validatingforminput.PersonForm",
	"com.ampp.businessValidationOverride[0].fields[0].fieldName=name",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[0].constraintType=NotNull",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[1].constraintType=NotBlank",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[2].constraintType=Size",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[2].params.min=4",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[2].params.max=40",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[3].constraintType=Pattern",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[3].params.regexp=^[A-Za-z]+$",
	"com.ampp.businessValidationOverride[0].fields[1].fieldName=age",
	"com.ampp.businessValidationOverride[0].fields[1].constraints[0].constraintType=NotNull",
	"com.ampp.businessValidationOverride[0].fields[1].constraints[1].constraintType=Min",
	"com.ampp.businessValidationOverride[0].fields[1].constraints[1].params.value=25",
	"com.ampp.businessValidationOverride[0].fields[1].constraints[2].constraintType=Max",
	"com.ampp.businessValidationOverride[0].fields[1].constraints[2].params.value=70"
})
@AutoConfigureMockMvc
public class ApplicationMockMvcTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PersonValidationService personValidationService;

	@Test
	public void shouldApplyEffectiveConstraintsForMvcValidation() throws Exception {
		mockMvc.perform(post("/").param("name", "Robs").param("age", "25"))
			.andExpect(model().hasNoErrors());

		mockMvc.perform(post("/").param("name", "Rob").param("age", "25"))
			.andExpect(model().attributeHasFieldErrors("personForm", "name"));

		mockMvc.perform(post("/").param("name", "Robert").param("age", "20"))
			.andExpect(model().attributeHasFieldErrors("personForm", "age"));

		mockMvc.perform(post("/").param("name", "Robert").param("age", "61"))
			.andExpect(model().attributeHasFieldErrors("personForm", "age"));

		mockMvc.perform(post("/").param("name", "John Doe").param("age", "25"))
			.andExpect(model().attributeHasFieldErrors("personForm", "name"));
	}

	@Test
	public void shouldUseConfiguredStartupValidatorForMethodValidation() {
		PersonForm form = new PersonForm();
		form.setName("Robs");
		form.setAge(20);

		assertThatThrownBy(() -> personValidationService.validate(form))
			.isInstanceOf(ConstraintViolationException.class);
	}
}
