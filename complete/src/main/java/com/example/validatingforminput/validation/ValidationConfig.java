package com.example.validatingforminput.validation;

import org.hibernate.validator.HibernateValidatorConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ValidationProperties.class)
public class ValidationConfig {

	private final ConfigDrivenConstraintMappingContributor contributor;

	public ValidationConfig(ConfigDrivenConstraintMappingContributor contributor) {
		this.contributor = contributor;
	}

	@Bean
	public ValidationConfigurationCustomizer validationConfigurationCustomizer() {
		return configuration -> {
            if (!(configuration instanceof HibernateValidatorConfiguration hibernateConfiguration)) {
                throw new IllegalStateException(
                    "Hibernate Validator configuration is required for config-driven constraint mapping.");
            }
            contributor.createConstraintMappings(() -> {
                var mapping = hibernateConfiguration.createConstraintMapping();
                hibernateConfiguration.addMapping(mapping);
                return mapping;
            });

        };
	}
}
