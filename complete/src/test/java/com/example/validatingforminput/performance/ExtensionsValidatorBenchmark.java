package com.example.validatingforminput.performance;

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

import com.example.validatingforminput.perf.PerfMapValidationRequest;
import com.example.validatingforminput.perf.PerfPayloadFixtures;
import com.example.validatingforminput.perf.PerfRawValidationRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ExtensionsValidatorBenchmark {

    private static final String MAP_REQUEST_CLASS_NAME = "com.example.validatingforminput.perf.PerfMapValidationRequest";
    private static final String RAW_REQUEST_CLASS_NAME = "com.example.validatingforminput.perf.PerfRawValidationRequest";
    private static final String EXTENSIONS_FIELD_NAME = "extensions";
    private static final String CODE_REGEX = "^[A-Z]{3}-[0-9]{4}$";

    @Benchmark
    public int off_map_shallow_payload(BenchmarkState state) {
        return countViolations(state.validationOffValidator, state.shallowMapRequest);
    }

    @Benchmark
    public int off_map_deep_payload(BenchmarkState state) {
        return countViolations(state.validationOffValidator, state.deepMapRequest);
    }

    @Benchmark
    public int baseline_map_shallow_payload(BenchmarkState state) {
        return countViolations(state.baselineValidator, state.shallowMapRequest);
    }

    @Benchmark
    public int baseline_map_deep_payload(BenchmarkState state) {
        return countViolations(state.baselineValidator, state.deepMapRequest);
    }

    @Benchmark
    public int shallow_path_on_map_shallow_payload(BenchmarkState state) {
        return countViolations(state.shallowValidator, state.shallowMapRequest);
    }

    @Benchmark
    public int shallow_path_on_map_deep_payload(BenchmarkState state) {
        return countViolations(state.shallowValidator, state.deepMapRequest);
    }

    @Benchmark
    public int deep_path_on_map_deep_payload(BenchmarkState state) {
        return countViolations(state.deepValidator, state.deepMapRequest);
    }

    @Benchmark
    public int off_json_shallow_payload(BenchmarkState state) {
        return countViolations(state.validationOffValidator, state.shallowRawRequest);
    }

    @Benchmark
    public int off_json_deep_payload(BenchmarkState state) {
        return countViolations(state.validationOffValidator, state.deepRawRequest);
    }

    @Benchmark
    public int baseline_json_shallow_payload(BenchmarkState state) {
        return countViolations(state.baselineValidator, state.shallowRawRequest);
    }

    @Benchmark
    public int baseline_json_deep_payload(BenchmarkState state) {
        return countViolations(state.baselineValidator, state.deepRawRequest);
    }

    @Benchmark
    public int shallow_path_on_json_shallow_payload(BenchmarkState state) {
        return countViolations(state.shallowValidator, state.shallowRawRequest);
    }

    @Benchmark
    public int shallow_path_on_json_deep_payload(BenchmarkState state) {
        return countViolations(state.shallowValidator, state.deepRawRequest);
    }

    @Benchmark
    public int deep_path_on_json_deep_payload(BenchmarkState state) {
        return countViolations(state.deepValidator, state.deepRawRequest);
    }

    @State(Scope.Benchmark)
    public static class BenchmarkState {

        private ConfigurableApplicationContext validationOffContext;
        private ConfigurableApplicationContext baselineContext;
        private ConfigurableApplicationContext shallowContext;
        private ConfigurableApplicationContext deepContext;

        private Validator validationOffValidator;
        private Validator baselineValidator;
        private Validator shallowValidator;
        private Validator deepValidator;

        private PerfMapValidationRequest shallowMapRequest;
        private PerfMapValidationRequest deepMapRequest;
        private PerfMapValidationRequest invalidShallowMapRequest;
        private PerfMapValidationRequest invalidDeepMapRequest;

        private PerfRawValidationRequest shallowRawRequest;
        private PerfRawValidationRequest deepRawRequest;
        private PerfRawValidationRequest invalidShallowRawRequest;
        private PerfRawValidationRequest invalidDeepRawRequest;
        private PerfRawValidationRequest malformedRawRequest;

        @Setup(Level.Trial)
        public void setUp() {
            validationOffContext = startContext(validationOffProperties());
            baselineContext = startContext(baselineProperties());
            shallowContext = startContext(extensionProperties("$.vendorExtensionCode"));
            deepContext = startContext(extensionProperties("$.vendor.contact.codes[*].value"));

            validationOffValidator = validationOffContext.getBean(Validator.class);
            baselineValidator = baselineContext.getBean(Validator.class);
            shallowValidator = shallowContext.getBean(Validator.class);
            deepValidator = deepContext.getBean(Validator.class);

            shallowMapRequest = PerfPayloadFixtures.shallowMapRequest();
            deepMapRequest = PerfPayloadFixtures.deepMapRequest();
            invalidShallowMapRequest = PerfPayloadFixtures.invalidShallowMapRequest();
            invalidDeepMapRequest = PerfPayloadFixtures.invalidDeepMapRequest();

            shallowRawRequest = PerfPayloadFixtures.shallowRawRequest();
            deepRawRequest = PerfPayloadFixtures.deepRawRequest();
            invalidShallowRawRequest = PerfPayloadFixtures.invalidShallowRawRequest();
            invalidDeepRawRequest = PerfPayloadFixtures.invalidDeepRawRequest();
            malformedRawRequest = PerfPayloadFixtures.malformedRawRequest();

            assertValid("validation off should allow invalid shallow map payload", validationOffValidator, invalidShallowMapRequest);
            assertValid("validation off should allow invalid deep map payload", validationOffValidator, invalidDeepMapRequest);
            assertValid("validation off should allow invalid shallow raw payload", validationOffValidator, invalidShallowRawRequest);
            assertValid("validation off should allow malformed raw payload", validationOffValidator, malformedRawRequest);

            assertValid("baseline validator should accept shallow map payload", baselineValidator, shallowMapRequest);
            assertValid("baseline validator should accept deep map payload", baselineValidator, deepMapRequest);
            assertValid("baseline validator should accept shallow raw payload", baselineValidator, shallowRawRequest);
            assertValid("baseline validator should accept deep raw payload", baselineValidator, deepRawRequest);
            assertValid("baseline validator should allow invalid shallow map extension payload", baselineValidator, invalidShallowMapRequest);
            assertValid("baseline validator should allow invalid deep raw extension payload", baselineValidator, invalidDeepRawRequest);
            assertValid("baseline validator should allow malformed raw extension payload", baselineValidator, malformedRawRequest);

            assertValid("shallow validator should accept shallow map payload", shallowValidator, shallowMapRequest);
            assertValid("shallow validator should accept deep map payload", shallowValidator, deepMapRequest);
            assertValid("shallow validator should accept shallow raw payload", shallowValidator, shallowRawRequest);
            assertValid("shallow validator should accept deep raw payload", shallowValidator, deepRawRequest);
            assertSingleViolation("shallow validator should reject invalid shallow map payload", shallowValidator, invalidShallowMapRequest);
            assertSingleViolation("shallow validator should reject invalid shallow raw payload", shallowValidator, invalidShallowRawRequest);
            assertSingleViolation("shallow validator should reject malformed raw payload", shallowValidator, malformedRawRequest);

            assertValid("deep validator should accept deep map payload", deepValidator, deepMapRequest);
            assertValid("deep validator should accept deep raw payload", deepValidator, deepRawRequest);
            assertSingleViolation("deep validator should reject invalid deep map payload", deepValidator, invalidDeepMapRequest);
            assertSingleViolation("deep validator should reject invalid deep raw payload", deepValidator, invalidDeepRawRequest);
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            close(validationOffContext);
            close(baselineContext);
            close(shallowContext);
            close(deepContext);
        }

        private static void close(ConfigurableApplicationContext context) {
            if (context != null) {
                context.close();
            }
        }

        private static void assertValid(String message, Validator validator, Object target) {
            Set<? extends ConstraintViolation<?>> violations = validator.validate(target);
            if (!violations.isEmpty()) {
                throw new IllegalStateException(message + ": " + violations);
            }
        }

        private static void assertSingleViolation(String message, Validator validator, Object target) {
            Set<? extends ConstraintViolation<?>> violations = validator.validate(target);
            if (violations.size() != 1) {
                throw new IllegalStateException(message + ": " + violations);
            }
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class BenchmarkApplication {
    }

    private static int countViolations(Validator validator, Object target) {
        return validator.validate(target).size();
    }

    private static ConfigurableApplicationContext startContext(String... properties) {
        return new SpringApplicationBuilder(BenchmarkApplication.class)
            .web(WebApplicationType.NONE)
            .properties(properties)
            .run();
    }

    private static String[] baselineProperties() {
        return new String[] {
            "spring.config.name=validation-benchmark",
            "spring.main.banner-mode=off",
            "logging.level.root=ERROR",
            "com.ampp.validation-enabled=true"
        };
    }

    private static String[] validationOffProperties() {
        return new String[] {
            "spring.config.name=validation-benchmark",
            "spring.main.banner-mode=off",
            "logging.level.root=ERROR",
            "com.ampp.validation-enabled=false"
        };
    }

    private static String[] extensionProperties(String jsonPath) {
        return new String[] {
            "spring.config.name=validation-benchmark",
            "spring.main.banner-mode=off",
            "logging.level.root=ERROR",
            "com.ampp.validation-enabled=true",
            extensionProperty(0, MAP_REQUEST_CLASS_NAME, jsonPath),
            "com.ampp.businessValidationOverride[0].fields[0].fieldName=" + EXTENSIONS_FIELD_NAME,
            "com.ampp.businessValidationOverride[0].fields[0].constraints[0].constraintType=Extensions",
            "com.ampp.businessValidationOverride[0].fields[0].constraints[0].params.jsonPath=" + jsonPath,
            "com.ampp.businessValidationOverride[0].fields[0].constraints[0].params.regexp=" + CODE_REGEX,
            extensionProperty(1, RAW_REQUEST_CLASS_NAME, jsonPath),
            "com.ampp.businessValidationOverride[1].fields[0].fieldName=" + EXTENSIONS_FIELD_NAME,
            "com.ampp.businessValidationOverride[1].fields[0].constraints[0].constraintType=Extensions",
            "com.ampp.businessValidationOverride[1].fields[0].constraints[0].params.jsonPath=" + jsonPath,
            "com.ampp.businessValidationOverride[1].fields[0].constraints[0].params.regexp=" + CODE_REGEX
        };
    }

    private static String extensionProperty(int index, String className, String jsonPath) {
        return "com.ampp.businessValidationOverride[" + index + "].fullClassName=" + className;
    }
}
