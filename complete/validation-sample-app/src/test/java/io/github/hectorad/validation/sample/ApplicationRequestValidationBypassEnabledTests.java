package io.github.hectorad.validation.sample;

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
		"hector.validation.http-bypass.enabled=true"
	}
)
@AutoConfigureMockMvc
class ApplicationRequestValidationBypassEnabledTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PersonValidationService personValidationService;

	@Test
	void shouldKeepMvcValidationActiveWhenHeaderIsAbsent() throws Exception {
		mockMvc.perform(post("/validation-probe/mvc").param("name", "").param("age", "5"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void shouldKeepMvcValidationActiveWhenHeaderValueDoesNotMatch() throws Exception {
		mockMvc.perform(post("/validation-probe/mvc")
				.header("X-Skip-Validation", "false")
				.param("name", "")
				.param("age", "5"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void shouldSkipMvcValidationWhenHeaderValueMatches() throws Exception {
		mockMvc.perform(post("/validation-probe/mvc")
				.header("X-Skip-Validation", "true")
				.param("name", "")
				.param("age", "5"))
			.andExpect(status().isOk());
	}

	@Test
	void shouldKeepMethodValidationActiveWhenHeaderIsAbsent() throws Exception {
		assertThatThrownBy(() -> mockMvc.perform(post("/validation-probe/method")))
			.hasCauseInstanceOf(ConstraintViolationException.class);
	}

	@Test
	void shouldKeepMethodValidationActiveWhenHeaderValueDoesNotMatch() throws Exception {
		assertThatThrownBy(() -> mockMvc.perform(post("/validation-probe/method").header("X-Skip-Validation", "false")))
			.hasCauseInstanceOf(ConstraintViolationException.class);
	}

	@Test
	void shouldSkipMethodValidationWhenHeaderValueMatches() throws Exception {
		mockMvc.perform(post("/validation-probe/method").header("X-Skip-Validation", "true"))
			.andExpect(status().isOk());
	}

	@Test
	void shouldKeepValidationActiveOutsideRequestContext() {
		PersonForm form = new PersonForm();
		form.setName("");
		form.setAge(5);

		assertThatThrownBy(() -> personValidationService.validate(form))
			.isInstanceOf(ConstraintViolationException.class);
	}
}
