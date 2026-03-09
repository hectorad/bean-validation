package com.example.validation.autoconfigure;

import com.example.validatingforminput.validation.ConfigDrivenConstraintMappingContributor;
import com.example.validatingforminput.validation.ConstraintMergeService;
import com.example.validatingforminput.validation.GeneratedClassMetadataCache;
import com.example.validatingforminput.validation.ValidationProperties;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
//@ConditionalOnClass({ ValidationConfigurationCustomizer.class, HibernateValidatorConfiguration.class })
@EnableConfigurationProperties(ValidationProperties.class)
public class ValidationAutoConfiguration {

	@Bean
//	@ConditionalOnMissingBean
	public ConstraintMergeService constraintMergeService() {
		return new ConstraintMergeService();
	}

	@Bean
//	@ConditionalOnMissingBean
	public GeneratedClassMetadataCache generatedClassMetadataCache(ValidationProperties validationProperties) {
		return new GeneratedClassMetadataCache(validationProperties);
	}

	@Bean
//	@ConditionalOnMissingBean
	public ConfigDrivenConstraintMappingContributor configDrivenConstraintMappingContributor(
		ValidationProperties validationProperties,
		GeneratedClassMetadataCache generatedClassMetadataCache,
		ConstraintMergeService constraintMergeService
	) {
		return new ConfigDrivenConstraintMappingContributor(
			validationProperties,
			generatedClassMetadataCache,
			constraintMergeService);
	}

	@Bean
//	@ConditionalOnMissingBean(name = "configDrivenValidationConfigurationCustomizer")
	public ValidationConfigurationCustomizer configDrivenValidationConfigurationCustomizer(
		ConfigDrivenConstraintMappingContributor contributor
	) {
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
