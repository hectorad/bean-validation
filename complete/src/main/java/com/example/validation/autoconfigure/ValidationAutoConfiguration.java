package com.example.validation.autoconfigure;

import com.example.validation.core.api.ExternalPayloadValidator;
import com.example.validation.core.internal.BeanValidationExternalPayloadValidator;
import com.example.validation.core.internal.ConfigDrivenConstraintMappingContributor;
import com.example.validation.core.internal.ConstraintMergeService;
import com.example.validation.core.internal.GeneratedClassMetadataCache;
import com.example.validation.core.internal.PropertiesValidationOverrideContributor;
import com.example.validation.core.internal.RequestAwareValidatingLocalValidatorFactoryBean;
import com.example.validation.core.internal.ValidationProperties;
import com.example.validation.core.internal.ValidationOverrideRegistry;
import com.example.validation.core.internal.ValidationTroubleshootingAnalyzer;
import com.example.validation.core.spi.ValidationOverrideContributor;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.validation.MessageInterpolatorFactory;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import jakarta.validation.Validator;

import java.util.List;

@AutoConfiguration(before = org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration.class)
@ConditionalOnClass({ValidationConfigurationCustomizer.class, HibernateValidatorConfiguration.class})
@EnableConfigurationProperties(ValidationProperties.class)
public class ValidationAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ValidationAutoConfiguration.class);

    @Bean(name = "defaultValidator")
    @Primary
    @RefreshScope
    @ConditionalOnMissingBean(name = "defaultValidator")
    public LocalValidatorFactoryBean defaultValidator(
            ApplicationContext applicationContext,
            ObjectProvider<ValidationConfigurationCustomizer> customizers,
            ValidationProperties validationProperties
    ) {
        if (!validationProperties.isValidationEnabled()) {
            log.warn("*** ALL VALIDATION IS DISABLED (com.ampp.validation-enabled=false). "
                    + "No constraints will be enforced on any field. ***");
        }
        return configureDefaultValidator(applicationContext, customizers, validationProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
    public ConstraintMergeService constraintMergeService() {
        return new ConstraintMergeService();
    }

    @Bean
    @ConditionalOnMissingBean(name = "propertiesValidationOverrideContributor")
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
    @RefreshScope
    public ValidationOverrideContributor propertiesValidationOverrideContributor(ValidationProperties validationProperties) {
        return new PropertiesValidationOverrideContributor(validationProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
    @RefreshScope
    public ValidationOverrideRegistry validationOverrideRegistry(
            ValidationProperties validationProperties,
            ObjectProvider<ValidationOverrideContributor> validationOverrideContributors
    ) {
        return new ValidationOverrideRegistry(
                defaultValidationOverrideContributors(validationProperties, validationOverrideContributors));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
    @RefreshScope
    public GeneratedClassMetadataCache generatedClassMetadataCache(
            ValidationOverrideRegistry validationOverrideRegistry
    ) {
        return new GeneratedClassMetadataCache(validationOverrideRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
    @RefreshScope
    public ConfigDrivenConstraintMappingContributor configDrivenConstraintMappingContributor(
            ValidationOverrideRegistry validationOverrideRegistry,
            GeneratedClassMetadataCache generatedClassMetadataCache,
            ConstraintMergeService constraintMergeService
    ) {
        return new ConfigDrivenConstraintMappingContributor(
                validationOverrideRegistry,
                generatedClassMetadataCache,
                constraintMergeService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
    public ValidationTroubleshootingAnalyzer validationTroubleshootingAnalyzer(
            ValidationOverrideRegistry validationOverrideRegistry,
            GeneratedClassMetadataCache generatedClassMetadataCache,
            ConstraintMergeService constraintMergeService
    ) {
        return new ValidationTroubleshootingAnalyzer(
                validationOverrideRegistry,
                generatedClassMetadataCache,
                constraintMergeService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExternalPayloadValidator externalPayloadValidator(Validator validator) {
        return new BeanValidationExternalPayloadValidator(validator);
    }

    @Bean
    public ValidationConfigurationCustomizer configDrivenValidationConfigurationCustomizer(
            ValidationProperties validationProperties,
            ObjectProvider<ConfigDrivenConstraintMappingContributor> contributorProvider,
            ObjectProvider<ValidationOverrideRegistry> validationOverrideRegistryProvider,
            ObjectProvider<GeneratedClassMetadataCache> generatedClassMetadataCacheProvider,
            ObjectProvider<ConstraintMergeService> constraintMergeServiceProvider,
            ObjectProvider<ValidationOverrideContributor> validationOverrideContributors
    ) {
        java.util.concurrent.atomic.AtomicBoolean warnedUnsupportedProvider = new java.util.concurrent.atomic.AtomicBoolean();
        return configuration -> {
            if (!validationProperties.isValidationEnabled()) {
                return;
            }
            if (!(configuration instanceof HibernateValidatorConfiguration hibernateConfiguration)) {
                if (warnedUnsupportedProvider.compareAndSet(false, true)) {
                    log.warn(
                            "Skipping config-driven constraint mapping because the active Bean Validation provider is not Hibernate Validator: {}",
                            configuration.getClass().getName());
                }
                return;
            }
            ConfigDrivenConstraintMappingContributor contributor = contributorProvider
                    .getIfAvailable(() -> runtimeConstraintMappingContributor(
                            validationProperties,
                            validationOverrideRegistryProvider,
                            generatedClassMetadataCacheProvider,
                            constraintMergeServiceProvider,
                            validationOverrideContributors));
            contributor.createConstraintMappings(() -> {
                var mapping = hibernateConfiguration.createConstraintMapping();
                hibernateConfiguration.addMapping(mapping);
                return mapping;
            });
        };
    }

    private static RequestAwareValidatingLocalValidatorFactoryBean configureDefaultValidator(
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

    private static ConfigDrivenConstraintMappingContributor runtimeConstraintMappingContributor(
            ValidationProperties validationProperties,
            ObjectProvider<ValidationOverrideRegistry> validationOverrideRegistryProvider,
            ObjectProvider<GeneratedClassMetadataCache> generatedClassMetadataCacheProvider,
            ObjectProvider<ConstraintMergeService> constraintMergeServiceProvider,
            ObjectProvider<ValidationOverrideContributor> validationOverrideContributors
    ) {
        ValidationOverrideRegistry validationOverrideRegistry = validationOverrideRegistryProvider
                .getIfAvailable(() -> new ValidationOverrideRegistry(
                        defaultValidationOverrideContributors(validationProperties, validationOverrideContributors)));
        GeneratedClassMetadataCache generatedClassMetadataCache = generatedClassMetadataCacheProvider
                .getIfAvailable(() -> new GeneratedClassMetadataCache(validationOverrideRegistry));
        ConstraintMergeService constraintMergeService = constraintMergeServiceProvider
                .getIfAvailable(ConstraintMergeService::new);
        return new ConfigDrivenConstraintMappingContributor(
                validationOverrideRegistry,
                generatedClassMetadataCache,
                constraintMergeService);
    }

    private static List<ValidationOverrideContributor> defaultValidationOverrideContributors(
            ValidationProperties validationProperties,
            ObjectProvider<ValidationOverrideContributor> validationOverrideContributors
    ) {
        List<ValidationOverrideContributor> contributors = validationOverrideContributors.orderedStream().toList();
        return contributors.isEmpty()
                ? List.of(new PropertiesValidationOverrideContributor(validationProperties))
                : contributors;
    }
}
