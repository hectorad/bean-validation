package io.github.hectorad.validation.sample;

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
	"hector.validation.overrides[0].class-name=io.github.hectorad.validation.sample.PersonForm",
	"hector.validation.overrides[0].fields[0].field-name=name",
	"hector.validation.overrides[0].fields[0].constraints.not-null.value=true",
	"hector.validation.overrides[0].fields[0].constraints.not-blank.value=true",
	"hector.validation.overrides[0].fields[0].constraints.size.min.value=4",
	"hector.validation.overrides[0].fields[0].constraints.size.max.value=40",
	"hector.validation.overrides[0].fields[0].constraints.pattern.regexes[0]=^[A-Za-z]+$",
	"hector.validation.overrides[0].fields[1].field-name=age",
	"hector.validation.overrides[0].fields[1].constraints.not-null.value=true",
	"hector.validation.overrides[0].fields[1].constraints.min.value=25",
	"hector.validation.overrides[0].fields[1].constraints.max.value=70"
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
