package com.example.validatingforminput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {
	"com.ampp.businessValidationOverride[0].fullClassName=com.example.validatingforminput.PersonForm",
	"com.ampp.businessValidationOverride[0].fields[0].fieldName=name",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[0].constraintType=NotNull",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[0].message=Name is required",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[1].constraintType=NotBlank",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[1].message=Name must not be blank",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[2].constraintType=Size",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[2].params.min=4",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[2].params.minMessage=Name must have at least 4 characters",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[2].params.max=6",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[2].params.maxMessage=Name must have at most 6 characters",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[3].constraintType=Pattern",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[3].params.regexp=^[A-Za-z]+$",
	"com.ampp.businessValidationOverride[0].fields[0].constraints[3].message=Name must contain letters only",
	"com.ampp.businessValidationOverride[0].fields[1].fieldName=age",
	"com.ampp.businessValidationOverride[0].fields[1].constraints[0].constraintType=Min",
	"com.ampp.businessValidationOverride[0].fields[1].constraints[0].params.value=25",
	"com.ampp.businessValidationOverride[0].fields[1].constraints[1].constraintType=Max",
	"com.ampp.businessValidationOverride[0].fields[1].constraints[1].params.value=60",
	"com.ampp.businessValidationOverride[0].fields[2].fieldName=salary",
	"com.ampp.businessValidationOverride[0].fields[2].constraints[0].constraintType=DecimalMin",
	"com.ampp.businessValidationOverride[0].fields[2].constraints[0].params.value=1000.01",
	"com.ampp.businessValidationOverride[0].fields[2].constraints[1].constraintType=DecimalMax",
	"com.ampp.businessValidationOverride[0].fields[2].constraints[1].params.value=250000.00"
})
@AutoConfigureMockMvc
class ApplicationCustomMessageMockMvcTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void shouldExposeCustomSizeAndPatternMessagesInBindingResult() throws Exception {
		MvcResult tooShortResult = mockMvc.perform(post("/")
			.param("name", "Abc")
			.param("age", "30")
			.param("salary", "2000.00"))
			.andExpect(model().attributeHasFieldErrors("personForm", "name"))
			.andReturn();
		assertThat(messagesForField(tooShortResult, "name")).contains("Name must have at least 4 characters");

		MvcResult tooLongResult = mockMvc.perform(post("/")
			.param("name", "ABCDEFG")
			.param("age", "30")
			.param("salary", "2000.00"))
			.andExpect(model().attributeHasFieldErrors("personForm", "name"))
			.andReturn();
		assertThat(messagesForField(tooLongResult, "name")).contains("Name must have at most 6 characters");

		MvcResult patternResult = mockMvc.perform(post("/")
			.param("name", "Abc1")
			.param("age", "30")
			.param("salary", "2000.00"))
			.andExpect(model().attributeHasFieldErrors("personForm", "name"))
			.andReturn();
		assertThat(messagesForField(patternResult, "name")).contains("Name must contain letters only");
	}

	private List<String> messagesForField(MvcResult result, String fieldName) {
		Object rawBindingResult = result.getModelAndView().getModel().get(BindingResult.MODEL_KEY_PREFIX + "personForm");
		assertThat(rawBindingResult).isInstanceOf(BindingResult.class);

		BindingResult bindingResult = (BindingResult) rawBindingResult;
		return bindingResult.getFieldErrors(fieldName).stream().map(FieldError::getDefaultMessage).toList();
	}
}
