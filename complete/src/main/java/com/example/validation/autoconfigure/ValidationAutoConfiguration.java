package com.example.validation.autoconfigure;

import com.example.validatingforminput.validation.*;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.validation.MessageInterpolatorFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@AutoConfiguration(before = org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration.class)
@ConditionalOnClass({ValidationConfigurationCustomizer.class, HibernateValidatorConfiguration.class})
@EnableConfigurationProperties(ValidationProperties.class)
public class ValidationAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ValidationAutoConfiguration.class);

    @Bean
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "false")
    public LocalValidatorFactoryBean noOpValidator() {
        log.warn("*** ALL VALIDATION IS DISABLED (com.ampp.validation-enabled=false). "
                + "No constraints will be enforced on any field. ***");
        return new NoOpValidatingLocalValidatorFactoryBean();
    }

    @Bean(name = "defaultValidator")
    @Primary
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
    public LocalValidatorFactoryBean defaultValidator(
            ApplicationContext applicationContext,
            ObjectProvider<ValidationConfigurationCustomizer> customizers,
            ValidationProperties validationProperties
    ) {
        RequestAwareValidatingLocalValidatorFactoryBean factoryBean =
                new RequestAwareValidatingLocalValidatorFactoryBean(validationProperties);
        factoryBean.setConfigurationInitializer(configuration ->
                customizers.orderedStream().forEach(customizer -> customizer.customize(configuration)));
        factoryBean.setMessageInterpolator(new MessageInterpolatorFactory(applicationContext).getObject());
        return factoryBean;
    }

    @Bean
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
    public ConstraintMergeService constraintMergeService() {
        return new ConstraintMergeService();
    }

    @Bean
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
    public GeneratedClassMetadataCache generatedClassMetadataCache(ValidationProperties validationProperties) {
        return new GeneratedClassMetadataCache(validationProperties, validationProperties.isFailOnError());
    }

    @Bean
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
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
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
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
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
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
