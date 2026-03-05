package com.example.validatingforminput;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import com.example.validatingforminput.validation.RefreshableValidator;
import com.example.validatingforminput.validation.ValidationProperties;

import jakarta.validation.ConstraintViolationException;

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {
	"validation.mappings[0].class-name=com.example.validatingforminput.PersonForm",
	"validation.mappings[0].fields[0].field-name=name",
	"validation.mappings[0].fields[0].constraints.not-null.value=true",
	"validation.mappings[0].fields[0].constraints.not-blank.value=true",
	"validation.mappings[0].fields[0].constraints.size.min.value=4",
	"validation.mappings[0].fields[0].constraints.size.max.value=40",
	"validation.mappings[0].fields[0].constraints.pattern.regexes[0]=^[A-Za-z]+$",
	"validation.mappings[0].fields[1].field-name=age",
	"validation.mappings[0].fields[1].constraints.not-null.value=true",
	"validation.mappings[0].fields[1].constraints.min.value=25",
	"validation.mappings[0].fields[1].constraints.min.hard-value=22",
	"validation.mappings[0].fields[1].constraints.max.value=70"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ApplicationMockMvcTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ValidationProperties validationProperties;

	@Autowired
	private RefreshableValidator refreshableValidator;

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
	public void shouldUseRefreshableValidatorForMethodValidation() {
		PersonForm form = new PersonForm();
		form.setName("Robs");
		form.setAge(20);

		assertThatThrownBy(() -> personValidationService.validate(form))
			.isInstanceOf(ConstraintViolationException.class);
	}

	@Test
	public void shouldRebuildValidatorAndKeepHardMinFloorOnRefresh() throws Exception {
		ValidationProperties.Constraints ageConstraints = fieldByName("age").getConstraints();
		ageConstraints.getMin().setValue(20L);
		refreshableValidator.refresh();

		mockMvc.perform(post("/").param("name", "Robs").param("age", "21"))
			.andExpect(model().attributeHasFieldErrors("personForm", "age"));

		mockMvc.perform(post("/").param("name", "Robs").param("age", "22"))
			.andExpect(model().hasNoErrors());
	}

	@Test
	public void shouldKeepPreviousValidatorWhenRefreshConfigIsInvalid() throws Exception {
		ValidationProperties.Constraints ageConstraints = fieldByName("age").getConstraints();
		ageConstraints.getMin().setValue(70L);
		ageConstraints.getMax().setValue(50L);
		refreshableValidator.refresh();

		mockMvc.perform(post("/").param("name", "Robs").param("age", "25"))
			.andExpect(model().hasNoErrors());

		mockMvc.perform(post("/").param("name", "Robs").param("age", "20"))
			.andExpect(model().attributeHasFieldErrors("personForm", "age"));
	}

	private ValidationProperties.FieldMapping fieldByName(String fieldName) {
		Map<String, ValidationProperties.FieldMapping> fieldsByName = validationProperties.getMappings().get(0).getFields().stream()
			.collect(Collectors.toMap(ValidationProperties.FieldMapping::getFieldName, Function.identity()));
		return fieldsByName.get(fieldName);
	}
}
