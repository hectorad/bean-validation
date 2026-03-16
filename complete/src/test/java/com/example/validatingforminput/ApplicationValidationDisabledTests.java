package com.example.validatingforminput;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
	classes = {ValidatingFormInputApplication.class, RequestValidationBypassTestConfiguration.class},
	properties = {
		"com.ampp.validation-enabled=false",
		"com.ampp.request-validation-bypass.enabled=true"
	}
)
@AutoConfigureMockMvc
public class ApplicationValidationDisabledTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PersonValidationService personValidationService;

	@Test
	public void shouldSkipMvcValidationWhenDisabled() throws Exception {
		mockMvc.perform(post("/").param("name", "").param("age", "5"))
			.andExpect(model().hasNoErrors())
			.andExpect(redirectedUrl("/results"));
	}

	@Test
	public void shouldSkipMvcValidationWithNullFieldsWhenDisabled() throws Exception {
		mockMvc.perform(post("/"))
			.andExpect(model().hasNoErrors())
			.andExpect(redirectedUrl("/results"));
	}

	@Test
	public void shouldSkipMvcValidationWhenGloballyDisabledEvenWithBypassHeader() throws Exception {
		mockMvc.perform(post("/validation-probe/mvc")
				.header("X-Skip-Validation", "true")
				.param("name", "")
				.param("age", "5"))
			.andExpect(status().isOk());
	}

	@Test
	public void shouldSkipMethodValidationWhenDisabled() {
		PersonForm form = new PersonForm();
		assertThatCode(() -> personValidationService.validate(form))
			.doesNotThrowAnyException();
	}

	@Test
	public void shouldSkipMethodValidationWithInvalidDataWhenDisabled() {
		PersonForm form = new PersonForm();
		form.setName("");
		form.setAge(5);
		assertThatCode(() -> personValidationService.validate(form))
			.doesNotThrowAnyException();
	}

	@Test
	public void shouldSkipMethodValidationWhenGloballyDisabledEvenWithBypassHeader() throws Exception {
		mockMvc.perform(post("/validation-probe/method").header("X-Skip-Validation", "true"))
			.andExpect(status().isOk());
	}
}
