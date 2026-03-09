package com.example.validatingforminput.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

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
			assertThat(context.containsBean("personValidationService")).isFalse();
			assertThat(context.containsBean("webController")).isFalse();
		}
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestApplication {
	}
}
