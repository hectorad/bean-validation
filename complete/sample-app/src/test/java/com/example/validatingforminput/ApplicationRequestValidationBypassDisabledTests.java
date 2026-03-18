package com.example.validatingforminput;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.validation.ConstraintViolationException;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
	classes = {ValidatingFormInputApplication.class, RequestValidationBypassTestConfiguration.class},
	properties = {
		"com.ampp.request-validation-bypass.enabled=false"
	}
)
@AutoConfigureMockMvc
class ApplicationRequestValidationBypassDisabledTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void shouldKeepMvcValidationActiveWhenHeaderIsAbsent() throws Exception {
		mockMvc.perform(post("/validation-probe/mvc").param("name", "").param("age", "5"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void shouldIgnoreBypassHeaderWhenFeatureIsDisabledForMvcValidation() throws Exception {
		mockMvc.perform(post("/validation-probe/mvc")
				.header("X-Skip-Validation", "true")
				.param("name", "")
				.param("age", "5"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void shouldKeepMethodValidationActiveWhenHeaderIsAbsent() throws Exception {
		assertThatThrownBy(() -> mockMvc.perform(post("/validation-probe/method")))
			.hasCauseInstanceOf(ConstraintViolationException.class);
	}

	@Test
	void shouldIgnoreBypassHeaderWhenFeatureIsDisabledForMethodValidation() throws Exception {
		assertThatThrownBy(() -> mockMvc.perform(post("/validation-probe/method").header("X-Skip-Validation", "true")))
			.hasCauseInstanceOf(ConstraintViolationException.class);
	}
}
