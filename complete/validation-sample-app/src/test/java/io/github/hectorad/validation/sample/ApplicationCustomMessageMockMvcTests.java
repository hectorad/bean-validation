package io.github.hectorad.validation.sample;

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
	"hector.validation.overrides[0].class-name=io.github.hectorad.validation.sample.PersonForm",
	"hector.validation.overrides[0].fields[0].field-name=name",
	"hector.validation.overrides[0].fields[0].constraints.not-null.value=true",
	"hector.validation.overrides[0].fields[0].constraints.not-null.message=Name is required",
	"hector.validation.overrides[0].fields[0].constraints.not-blank.value=true",
	"hector.validation.overrides[0].fields[0].constraints.not-blank.message=Name must not be blank",
	"hector.validation.overrides[0].fields[0].constraints.size.min.value=4",
	"hector.validation.overrides[0].fields[0].constraints.size.min.message=Name must have at least 4 characters",
	"hector.validation.overrides[0].fields[0].constraints.size.max.value=6",
	"hector.validation.overrides[0].fields[0].constraints.size.max.message=Name must have at most 6 characters",
	"hector.validation.overrides[0].fields[0].constraints.pattern.regexes[0]=^[A-Za-z]+$",
	"hector.validation.overrides[0].fields[0].constraints.pattern.message=Name must contain letters only",
	"hector.validation.overrides[0].fields[1].field-name=age",
	"hector.validation.overrides[0].fields[1].constraints.min.value=25",
	"hector.validation.overrides[0].fields[1].constraints.max.value=60",
	"hector.validation.overrides[0].fields[2].field-name=salary",
	"hector.validation.overrides[0].fields[2].constraints.decimal-min.value=1000.01",
	"hector.validation.overrides[0].fields[2].constraints.decimal-max.value=250000.00"
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
