package com.example.validatingforminput.performance;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.example.validatingforminput.PersonForm;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ExtensionsValidatorBenchmark {

	private static final String PERSON_FORM_CLASS_NAME = "com.example.validatingforminput.PersonForm";

	private static final String EXTENSIONS_FIELD_NAME = "extensions";

	private static final String CODE_REGEX = "^[A-Z]{3}-[0-9]{4}$";

	static {
		ensureResultsDirectoryExists();
	}

	@Benchmark
	public int baseline_shallow_payload(BenchmarkState state) {
		return state.baselineValidator.validate(state.shallowForm).size();
	}

	@Benchmark
	public int shallow_path_on_shallow_payload(BenchmarkState state) {
		return state.shallowValidator.validate(state.shallowForm).size();
	}

	@Benchmark
	public int baseline_deep_payload(BenchmarkState state) {
		return state.baselineValidator.validate(state.deepForm).size();
	}

	@Benchmark
	public int shallow_path_on_deep_payload(BenchmarkState state) {
		return state.shallowValidator.validate(state.deepForm).size();
	}

	@Benchmark
	public int deep_path_on_deep_payload(BenchmarkState state) {
		return state.deepValidator.validate(state.deepForm).size();
	}

	@State(Scope.Benchmark)
	public static class BenchmarkState {

		private ConfigurableApplicationContext baselineContext;

		private ConfigurableApplicationContext shallowContext;

		private ConfigurableApplicationContext deepContext;

		private Validator baselineValidator;

		private Validator shallowValidator;

		private Validator deepValidator;

		private PersonForm shallowForm;

		private PersonForm deepForm;

		@Setup(Level.Trial)
		public void setUp() {
			baselineContext = startContext(baseProperties());
			shallowContext = startContext(extensionProperties("$.vendorExtensionCode"));
			deepContext = startContext(extensionProperties("$.vendor.contact.codes[*].value"));

			baselineValidator = baselineContext.getBean(Validator.class);
			shallowValidator = shallowContext.getBean(Validator.class);
			deepValidator = deepContext.getBean(Validator.class);

			shallowForm = validForm(shallowPayload());
			deepForm = validForm(deepPayload());

			assertValid("baseline validator should accept shallow payload", baselineValidator, shallowForm);
			assertValid("baseline validator should accept deep payload", baselineValidator, deepForm);
			assertValid("shallow validator should accept shallow payload", shallowValidator, shallowForm);
			assertValid("shallow validator should accept deep payload", shallowValidator, deepForm);
			assertValid("deep validator should accept deep payload", deepValidator, deepForm);
			assertSingleViolation(
				"shallow validator should reject invalid shallow payload",
				shallowValidator,
				validForm(invalidShallowPayload()));
			assertSingleViolation(
				"deep validator should reject invalid deep payload",
				deepValidator,
				validForm(invalidDeepPayload()));
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			close(baselineContext);
			close(shallowContext);
			close(deepContext);
		}

		private static void close(ConfigurableApplicationContext context) {
			if (context != null) {
				context.close();
			}
		}

		private static void assertValid(String message, Validator validator, PersonForm form) {
			Set<ConstraintViolation<PersonForm>> violations = validator.validate(form);
			if (!violations.isEmpty()) {
				throw new IllegalStateException(message + ": " + violations);
			}
		}

		private static void assertSingleViolation(String message, Validator validator, PersonForm form) {
			Set<ConstraintViolation<PersonForm>> violations = validator.validate(form);
			if (violations.size() != 1) {
				throw new IllegalStateException(message + ": " + violations);
			}
		}
	}

	@SpringBootConfiguration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class BenchmarkApplication {
	}

	private static ConfigurableApplicationContext startContext(String... properties) {
		return new SpringApplicationBuilder(BenchmarkApplication.class)
			.web(WebApplicationType.NONE)
			.properties(properties)
			.run();
	}

	private static String[] baseProperties() {
		return new String[] {
			"spring.config.name=validation-benchmark",
			"spring.main.banner-mode=off",
			"logging.level.root=ERROR"
		};
	}

	private static String[] extensionProperties(String jsonPath) {
		return new String[] {
			"spring.config.name=validation-benchmark",
			"spring.main.banner-mode=off",
			"logging.level.root=ERROR",
			"com.ampp.businessValidationOverride[0].fullClassName=" + PERSON_FORM_CLASS_NAME,
			"com.ampp.businessValidationOverride[0].fields[0].fieldName=" + EXTENSIONS_FIELD_NAME,
			"com.ampp.businessValidationOverride[0].fields[0].constraints[0].constraintType=Extensions",
			"com.ampp.businessValidationOverride[0].fields[0].constraints[0].params.jsonPath=" + jsonPath,
			"com.ampp.businessValidationOverride[0].fields[0].constraints[0].params.regexp=" + CODE_REGEX
		};
	}

	private static PersonForm validForm(Map<String, Object> extensions) {
		PersonForm form = new PersonForm();
		form.setName("Robert");
		form.setAge(30);
		form.setSalary(new BigDecimal("2000.00"));
		form.setExtensions(extensions);
		return form;
	}

	private static Map<String, Object> shallowPayload() {
		return Map.of("vendorExtensionCode", "ABC-1234");
	}

	private static Map<String, Object> invalidShallowPayload() {
		return Map.of("vendorExtensionCode", "abc-1234");
	}

	private static Map<String, Object> deepPayload() {
		return Map.of(
			"vendorExtensionCode", "ABC-1234",
			"vendor", Map.of(
				"contact", Map.of(
					"codes", List.of(
						Map.of("value", "ABC-1234"),
						Map.of("value", "DEF-5678"),
						Map.of("value", "GHI-9012")))));
	}

	private static Map<String, Object> invalidDeepPayload() {
		return Map.of(
			"vendorExtensionCode", "ABC-1234",
			"vendor", Map.of(
				"contact", Map.of(
					"codes", List.of(
						Map.of("value", "ABC-1234"),
						Map.of("value", "DEF-5678"),
						Map.of("value", "ghi-9012")))));
	}

	private static void ensureResultsDirectoryExists() {
		try {
			Files.createDirectories(Path.of("target", "jmh-results"));
		}
		catch (IOException exception) {
			throw new ExceptionInInitializerError(exception);
		}
	}
}
