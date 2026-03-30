package com.example.validation.core.internal;

import com.example.validation.core.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import jakarta.validation.Configuration;
import jakarta.validation.Validator;

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
			assertThat(userBeanNamesForType(context, ConfigDrivenConstraintMappingContributor.class)).hasSize(1);
			assertThat(userBeanNamesForType(context, ConstraintMergeService.class)).hasSize(1);
			assertThat(userBeanNamesForType(context, ValidationOverrideRegistry.class)).hasSize(1);
			assertThat(userBeanNamesForType(context, GeneratedClassMetadataCache.class)).hasSize(1);
			assertThat(context.getBeansOfType(ExternalPayloadValidator.class)).hasSize(1);
			assertThat(userBeanNamesForType(context, com.example.validation.core.spi.ValidationOverrideContributor.class)).hasSize(1);
			assertThat(context.getBean(targetBeanName(context, "defaultValidator")))
				.isInstanceOf(RequestAwareValidatingLocalValidatorFactoryBean.class);
			assertThat(context.containsBean("personValidationService")).isFalse();
			assertThat(context.containsBean("webController")).isFalse();
		}
	}

	@Test
	void shouldProvideSameValidatorTypeWhenValidationIsDisabled() {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestApplication.class)
			.web(WebApplicationType.NONE)
			.run("--com.ampp.validation-enabled=false")) {
			LocalValidatorFactoryBean validator = context.getBean("defaultValidator", LocalValidatorFactoryBean.class);

			assertThat(context.getBean(targetBeanName(context, "defaultValidator")))
				.isInstanceOf(NoopLocalValidatorFactoryBean.class);
			assertThat(context.getBeansOfType(ConstraintMergeService.class)).isEmpty();
			assertThat(context.getBeansOfType(ValidationOverrideRegistry.class)).isEmpty();
			assertThat(context.getBeansOfType(GeneratedClassMetadataCache.class)).isEmpty();
			assertThat(context.getBeansOfType(com.example.validation.core.spi.ValidationOverrideContributor.class)).isEmpty();
		}
	}

	@Test
	void shouldBackOffToUserProvidedConstraintMergeService() {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(CustomConstraintMergeServiceApplication.class)
			.web(WebApplicationType.NONE)
			.run()) {
			assertThat(context.getBeansOfType(ConstraintMergeService.class)).hasSize(1);
			Object contributor = context.getBean(targetBeanName(context, "configDrivenConstraintMappingContributor"));
			assertThat(ReflectionTestUtils.getField(contributor, "constraintMergeService"))
				.isSameAs(context.getBean("customConstraintMergeService"));
		}
	}

	@Test
	void shouldBackOffToUserProvidedDefaultValidator() {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(CustomDefaultValidatorApplication.class)
			.web(WebApplicationType.NONE)
			.run()) {
			LocalValidatorFactoryBean validator = context.getBean("defaultValidator", LocalValidatorFactoryBean.class);

			assertThat(validator).isNotInstanceOf(RequestAwareValidatingLocalValidatorFactoryBean.class);
			assertThat(context.getBeansOfType(LocalValidatorFactoryBean.class)).hasSize(1);
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

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class CustomDefaultValidatorApplication {

		@Bean(name = "defaultValidator")
		LocalValidatorFactoryBean customDefaultValidator() {
			return new LocalValidatorFactoryBean();
		}
	}

	private static String[] userBeanNamesForType(ConfigurableApplicationContext context, Class<?> type) {
		return Arrays.stream(context.getBeanNamesForType(type))
			.filter(beanName -> !beanName.startsWith("scopedTarget."))
			.toArray(String[]::new);
	}

	private static String targetBeanName(ConfigurableApplicationContext context, String beanName) {
		String scopedTargetBeanName = "scopedTarget." + beanName;
		return context.containsBean(scopedTargetBeanName) ? scopedTargetBeanName : beanName;
	}
}
