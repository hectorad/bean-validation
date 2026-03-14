package com.example.validation.autoconfigure;

import com.example.validatingforminput.validation.ConfigDrivenConstraintMappingContributor;
import com.example.validatingforminput.validation.ConstraintMergeService;
import com.example.validatingforminput.validation.GeneratedClassMetadataCache;
import com.example.validatingforminput.validation.ValidationProperties;
import com.example.validatingforminput.validation.ValidationTroubleshootingAnalyzer;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({ ValidationConfigurationCustomizer.class, HibernateValidatorConfiguration.class })
@EnableConfigurationProperties(ValidationProperties.class)
public class ValidationAutoConfiguration {

	private static final Logger log = LoggerFactory.getLogger(ValidationAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean(ConstraintMergeService.class)
	public ConstraintMergeService constraintMergeService() {
		return new ConstraintMergeService();
	}

	@Bean
	@ConditionalOnMissingBean(GeneratedClassMetadataCache.class)
	public GeneratedClassMetadataCache generatedClassMetadataCache(ValidationProperties validationProperties) {
		return new GeneratedClassMetadataCache(validationProperties, validationProperties.isFailOnError());
	}

	@Bean
	@ConditionalOnMissingBean(ConfigDrivenConstraintMappingContributor.class)
	public ConfigDrivenConstraintMappingContributor configDrivenConstraintMappingContributor(
		ValidationProperties validationProperties,
		GeneratedClassMetadataCache generatedClassMetadataCache,
		ConstraintMergeService constraintMergeService
	) {
		return new ConfigDrivenConstraintMappingContributor(
			validationProperties,
			generatedClassMetadataCache,
			constraintMergeService,
			validationProperties.isFailOnError());
	}

	@Bean
	@ConditionalOnMissingBean(ValidationTroubleshootingAnalyzer.class)
	public ValidationTroubleshootingAnalyzer validationTroubleshootingAnalyzer(
		ValidationProperties validationProperties,
		GeneratedClassMetadataCache generatedClassMetadataCache,
		ConstraintMergeService constraintMergeService
	) {
		return new ValidationTroubleshootingAnalyzer(
			validationProperties,
			generatedClassMetadataCache,
			constraintMergeService);
	}

	@Bean
	@ConditionalOnMissingBean(name = "configDrivenValidationConfigurationCustomizer")
	public ValidationConfigurationCustomizer configDrivenValidationConfigurationCustomizer(
		ConfigDrivenConstraintMappingContributor contributor
	) {
		java.util.concurrent.atomic.AtomicBoolean warnedUnsupportedProvider = new java.util.concurrent.atomic.AtomicBoolean();
		return configuration -> {
			if (!(configuration instanceof HibernateValidatorConfiguration hibernateConfiguration)) {
				if (warnedUnsupportedProvider.compareAndSet(false, true)) {
					log.warn(
						"Skipping config-driven constraint mapping because the active Bean Validation provider is not Hibernate Validator: {}",
						configuration.getClass().getName());
				}
				return;
			}
			contributor.createConstraintMappings(() -> {
				var mapping = hibernateConfiguration.createConstraintMapping();
				hibernateConfiguration.addMapping(mapping);
				return mapping;
			});
		};
	}
}
