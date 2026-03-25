package com.example.validatingforminput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.example.validation.core.api.ExternalPayloadValidator;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

class ValidationRefreshIntegrationTests {

	private static final String CONFIG_NAME = "refresh-test";

	@Test
	void shouldReloadConstraintMappingsForMvcMethodAndExternalValidation(@TempDir Path tempDir) throws Exception {
		try (RunningApp app = startApp(tempDir, validationConfig(true, 25L, "X-Skip-Validation", "true"))) {
			PersonForm form = validPerson(27);

			app.mockMvc().perform(post("/validation-probe/mvc").param("name", "Robs").param("age", "27"))
				.andExpect(status().isOk());
			assertThatCode(() -> app.personValidationService().validate(form)).doesNotThrowAnyException();
			assertThat(app.externalPayloadValidator().validate(form).valid()).isTrue();

			app.writeConfig(validationConfig(true, 30L, "X-Skip-Validation", "true"));
			Set<String> refreshedKeys = app.refresh();

			assertThat(refreshedKeys).contains("com.ampp.business-validation-override[0].fields[0].constraints.min.value");
			app.mockMvc().perform(post("/validation-probe/mvc").param("name", "Robs").param("age", "27"))
				.andExpect(status().isBadRequest());
			assertThatThrownBy(() -> app.personValidationService().validate(form))
				.isInstanceOf(ConstraintViolationException.class);
			assertThat(app.externalPayloadValidator().validate(form).valid()).isFalse();
		}
	}

	@Test
	void shouldEnableValidationLiveAfterStartingDisabled(@TempDir Path tempDir) throws Exception {
		try (RunningApp app = startApp(tempDir, validationConfig(false, 25L, "X-Skip-Validation", "true"))) {
			PersonForm form = invalidPerson();

			assertThat(app.validator().validate(form)).isEmpty();
			assertThatCode(() -> app.personValidationService().validate(form)).doesNotThrowAnyException();
			app.mockMvc().perform(post("/validation-probe/mvc").param("name", "").param("age", "5"))
				.andExpect(status().isOk());

			app.writeConfig(validationConfig(true, 25L, "X-Skip-Validation", "true"));
			app.refresh();

			assertThat(app.validator().validate(form)).isNotEmpty();
			assertThatThrownBy(() -> app.personValidationService().validate(form))
				.isInstanceOf(ConstraintViolationException.class);
			app.mockMvc().perform(post("/validation-probe/mvc").param("name", "").param("age", "5"))
				.andExpect(status().isBadRequest());
		}
	}

	@Test
	void shouldReloadRequestValidationBypassHeaderNameAndValue(@TempDir Path tempDir) throws Exception {
		try (RunningApp app = startApp(tempDir, validationConfig(true, 25L, "X-Skip-Validation", "true"))) {
			app.mockMvc().perform(post("/validation-probe/mvc")
					.header("X-Skip-Validation", "true")
					.param("name", "")
					.param("age", "5"))
				.andExpect(status().isOk());

			app.writeConfig(validationConfig(true, 25L, "X-Dynamic-Bypass", "reload"));
			app.refresh();

			app.mockMvc().perform(post("/validation-probe/mvc")
					.header("X-Skip-Validation", "true")
					.param("name", "")
					.param("age", "5"))
				.andExpect(status().isBadRequest());
			app.mockMvc().perform(post("/validation-probe/mvc")
					.header("X-Dynamic-Bypass", "reload")
					.param("name", "")
					.param("age", "5"))
				.andExpect(status().isOk());
		}
	}

	@Test
	void shouldRequireAnotherRefreshAfterInvalidConfigurationIsFixed(@TempDir Path tempDir) throws Exception {
		try (RunningApp app = startApp(tempDir, validationConfig(true, 25L, "X-Skip-Validation", "true"))) {
			PersonForm form = validPerson(27);

			assertThat(app.validator().validate(form)).isEmpty();

			app.writeConfig(invalidValidationConfig());
			app.refresh();

			assertThatThrownBy(() -> app.validator().validate(form))
				.hasStackTraceContaining("Configured field was not found");

			app.writeConfig(validationConfig(true, 25L, "X-Skip-Validation", "true"));
			app.refresh();

			assertThat(app.validator().validate(form)).isEmpty();
		}
	}

	@Test
	void shouldAllowConcurrentValidationDuringRefresh(@TempDir Path tempDir) throws Exception {
		try (RunningApp app = startApp(tempDir, validationConfig(true, 25L, "X-Skip-Validation", "true"))) {
			PersonForm form = validPerson(27);
			Queue<Integer> observedViolationCounts = new ConcurrentLinkedQueue<>();
			Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
			AtomicBoolean running = new AtomicBoolean(true);
			CountDownLatch startSignal = new CountDownLatch(1);
			ExecutorService executorService = Executors.newFixedThreadPool(4);

			for (int index = 0; index < 4; index++) {
				executorService.submit(() -> {
					try {
						startSignal.await();
						while (running.get()) {
							observedViolationCounts.add(app.validator().validate(form).size());
						}
					}
					catch (Throwable throwable) {
						failures.add(throwable);
					}
				});
			}

			startSignal.countDown();
			Thread.sleep(150);

			app.writeConfig(validationConfig(true, 30L, "X-Skip-Validation", "true"));
			app.refresh();

			Thread.sleep(150);
			running.set(false);
			executorService.shutdownNow();
			assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

			assertThat(failures).isEmpty();
			assertThat(observedViolationCounts).isNotEmpty();
			assertThat(observedViolationCounts).contains(0, 1);
			assertThat(observedViolationCounts).allMatch(count -> count == 0 || count == 1);
		}
	}

	private static RunningApp startApp(Path tempDir, String configContent) throws IOException {
		Path configFile = tempDir.resolve(CONFIG_NAME + ".properties");
		Files.writeString(configFile, configContent, StandardCharsets.UTF_8);

		ConfigurableApplicationContext context = new SpringApplicationBuilder(
			ValidatingFormInputApplication.class,
			RequestValidationBypassTestConfiguration.class)
			.web(WebApplicationType.SERVLET)
			.properties(
				"server.port=0",
				"spring.config.name=" + CONFIG_NAME,
				"spring.config.additional-location=optional:file:" + tempDir.toAbsolutePath() + "/")
			.run();

		MockMvc mockMvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
		return new RunningApp(
			context,
			mockMvc,
			context.getBean(ContextRefresher.class),
			context.getBean(PersonValidationService.class),
			context.getBean(Validator.class),
			context.getBean(ExternalPayloadValidator.class),
			configFile);
	}

	private static PersonForm validPerson(int age) {
		PersonForm personForm = new PersonForm();
		personForm.setName("Robs");
		personForm.setAge(age);
		return personForm;
	}

	private static PersonForm invalidPerson() {
		PersonForm personForm = new PersonForm();
		personForm.setName("");
		personForm.setAge(5);
		return personForm;
	}

	private static String validationConfig(
		boolean validationEnabled,
		long minimumAge,
		String bypassHeaderName,
		String bypassHeaderValue
	) {
		return """
			com.ampp.validation-enabled=%s
			com.ampp.request-validation-bypass.enabled=true
			com.ampp.request-validation-bypass.header-name=%s
			com.ampp.request-validation-bypass.header-value=%s
			com.ampp.business-validation-override[0].full-class-name=com.example.validatingforminput.PersonForm
			com.ampp.business-validation-override[0].fields[0].field-name=age
			com.ampp.business-validation-override[0].fields[0].constraints.min.value=%s
			""".formatted(validationEnabled, bypassHeaderName, bypassHeaderValue, minimumAge);
	}

	private static String invalidValidationConfig() {
		return """
			com.ampp.validation-enabled=true
			com.ampp.request-validation-bypass.enabled=true
			com.ampp.request-validation-bypass.header-name=X-Skip-Validation
			com.ampp.request-validation-bypass.header-value=true
			com.ampp.business-validation-override[0].full-class-name=com.example.validatingforminput.PersonForm
			com.ampp.business-validation-override[0].fields[0].field-name=doesNotExist
			com.ampp.business-validation-override[0].fields[0].constraints.min.value=30
			""";
	}

	private record RunningApp(
		ConfigurableApplicationContext context,
		MockMvc mockMvc,
		ContextRefresher contextRefresher,
		PersonValidationService personValidationService,
		Validator validator,
		ExternalPayloadValidator externalPayloadValidator,
		Path configFile
	) implements AutoCloseable {

		void writeConfig(String configContent) throws IOException {
			Files.writeString(configFile, configContent, StandardCharsets.UTF_8);
		}

		Set<String> refresh() {
			return contextRefresher.refresh();
		}

		@Override
		public void close() {
			context.close();
		}
	}
}
