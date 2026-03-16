package com.example.validatingforminput.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import jakarta.validation.Configuration;

@ExtendWith(OutputCaptureExtension.class)
class ValidationAutoConfigurationDiscoveryTests {

	@Test
	void shouldProvideValidationBeansViaAutoConfigurationWithoutComponentScan() {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestApplication.class)
			.web(WebApplicationType.NONE)
			.run()) {
			assertThat(context.containsBean("configDrivenValidationConfigurationCustomizer")).isTrue();
			assertThat(context.getBean("configDrivenValidationConfigurationCustomizer", ValidationConfigurationCustomizer.class))
				.isNotNull();
			assertThat(context.getBeansOfType(ConfigDrivenConstraintMappingContributor.class)).hasSize(1);
			assertThat(context.getBeansOfType(ConstraintMergeService.class)).hasSize(1);
			assertThat(context.getBeansOfType(GeneratedClassMetadataCache.class)).hasSize(1);
			assertThat(context.getBean("defaultValidator", LocalValidatorFactoryBean.class))
				.isInstanceOf(RequestAwareValidatingLocalValidatorFactoryBean.class);
			assertThat(context.containsBean("personValidationService")).isFalse();
			assertThat(context.containsBean("webController")).isFalse();
		}
	}



	@Test
	void shouldWarnAndSkipWhenCustomizerReceivesNonHibernateConfiguration(CapturedOutput output) {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestApplication.class)
			.web(WebApplicationType.NONE)
			.run()) {
			ValidationConfigurationCustomizer customizer =
				context.getBean("configDrivenValidationConfigurationCustomizer", ValidationConfigurationCustomizer.class);

			@SuppressWarnings("unchecked")
			Configuration<?> configuration = Mockito.mock(Configuration.class);
			customizer.customize(configuration);

			assertThat(output.getOut())
				.contains("Skipping config-driven constraint mapping because the active Bean Validation provider is not Hibernate Validator");
		}
	}

	@Test
	void shouldKeepConfigurationPropertiesValidationActiveWithoutRequestContext() {
		assertThatThrownBy(() -> {
			try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestApplication.class)
				.web(WebApplicationType.NONE)
				.properties(
					"spring.config.name=validation-auto-config-test",
					"com.ampp.request-validation-bypass.enabled=true",
					"com.ampp.business-validation-override[0].full-class-name=",
					"com.ampp.business-validation-override[0].fields[0].field-name=name",
					"com.ampp.business-validation-override[0].fields[0].constraints.not-null.value=true")
				.run()) {
				assertThat(context).isNotNull();
			}
		})
			.hasStackTraceContaining("businessValidationOverride[0].fullClassName")
			.hasStackTraceContaining("must not be blank");
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestApplication {
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class CustomConstraintMergeServiceApplication {

		@Bean
		ConstraintMergeService customConstraintMergeService() {
			return new ConstraintMergeService();
		}
	}
}
