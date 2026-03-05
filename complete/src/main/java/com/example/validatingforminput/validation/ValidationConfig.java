package com.example.validatingforminput.validation;

import org.hibernate.validator.HibernateValidatorConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(ValidationProperties.class)
public class ValidationConfig {

	private final ConfigDrivenConstraintMappingContributor contributor;

	public ValidationConfig(ConfigDrivenConstraintMappingContributor contributor) {
		this.contributor = contributor;
	}

	@Bean
	@Primary
	public LocalValidatorFactoryBean validator() {
		LocalValidatorFactoryBean validatorFactoryBean = new LocalValidatorFactoryBean();
		validatorFactoryBean.setConfigurationInitializer(configuration -> {
			if (configuration instanceof HibernateValidatorConfiguration hibernateConfiguration) {
				contributor.createConstraintMappings(() -> {
					var mapping = hibernateConfiguration.createConstraintMapping();
					hibernateConfiguration.addMapping(mapping);
					return mapping;
				});
			}
			else {
				throw new IllegalStateException(
					"Hibernate Validator configuration is required for config-driven constraint mapping.");
			}
		});
		validatorFactoryBean.afterPropertiesSet();
		return validatorFactoryBean;
	}

	@Bean
	public WebMvcConfigurer webMvcValidationConfigurer(LocalValidatorFactoryBean validator) {
		return new WebMvcConfigurer() {
			@Override
			public Validator getValidator() {
				return validator;
			}
		};
	}
}
