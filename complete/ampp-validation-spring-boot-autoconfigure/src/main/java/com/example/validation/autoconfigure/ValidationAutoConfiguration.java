package com.example.validation.autoconfigure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.example.validatingforminput.validation.*;
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
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import jakarta.validation.Validator;

@AutoConfiguration(before = org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration.class)
@ConditionalOnClass({ValidationConfigurationCustomizer.class, HibernateValidatorConfiguration.class})
@EnableConfigurationProperties(ValidationProperties.class)
public class ValidationAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ValidationAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(ValidationBypassStrategy.class)
    @ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
    @ConditionalOnProperty(name = "com.ampp.request-validation-bypass.enabled", havingValue = "true")
    public ValidationBypassStrategy requestHeaderValidationBypassStrategy(ValidationProperties validationProperties) {
        ValidationProperties.RequestValidationBypass bypass = validationProperties.getRequestValidationBypass();
        return new RequestHeaderValidationBypassStrategy(bypass.getHeaderName(), bypass.getHeaderValue());
    }

    @Bean(name = "defaultValidator")
    @Primary
    @ConditionalOnMissingBean(name = "defaultValidator")
    public LocalValidatorFactoryBean defaultValidator(
            ApplicationContext applicationContext,
            ObjectProvider<ValidationConfigurationCustomizer> customizers,
            ObjectProvider<ValidationBypassStrategy> bypassStrategyProvider,
            ValidationProperties validationProperties
    ) {
        if (!validationProperties.isValidationEnabled()) {
            log.warn("*** ALL VALIDATION IS DISABLED (com.ampp.validation-enabled=false). "
                    + "No constraints will be enforced on any field. ***");
        }
        RequestAwareValidatingLocalValidatorFactoryBean factoryBean =
                new RequestAwareValidatingLocalValidatorFactoryBean(validationProperties, bypassStrategyProvider.getIfAvailable());
        factoryBean.setConfigurationInitializer(configuration ->
                customizers.orderedStream().forEach(customizer -> customizer.customize(configuration)));
        factoryBean.setMessageInterpolator(new MessageInterpolatorFactory(applicationContext).getObject());
        return factoryBean;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
    public PropertiesValidationOverrideContributor propertiesValidationOverrideContributor(
            ValidationProperties validationProperties
    ) {
        return new PropertiesValidationOverrideContributor(validationProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
    public ConstraintMergeService constraintMergeService() {
        return new ConstraintMergeService();
    }

    @Bean
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
    public List<ClassValidationOverride> mergedValidationOverrides(
            ObjectProvider<ValidationOverrideContributor> contributorProvider
    ) {
        return mergeContributorOverrides(contributorProvider.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
    public GeneratedClassMetadataCache generatedClassMetadataCache(
            ValidationProperties validationProperties,
            List<ClassValidationOverride> mergedValidationOverrides
    ) {
        return new GeneratedClassMetadataCache(mergedValidationOverrides, validationProperties.isFailOnError());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
    public ConfigDrivenConstraintMappingContributor configDrivenConstraintMappingContributor(
            ValidationProperties validationProperties,
            GeneratedClassMetadataCache generatedClassMetadataCache,
            ConstraintMergeService constraintMergeService,
            List<ClassValidationOverride> mergedValidationOverrides
    ) {
        return new ConfigDrivenConstraintMappingContributor(
                mergedValidationOverrides,
                generatedClassMetadataCache,
                constraintMergeService,
                validationProperties.isFailOnError());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
    public ValidationTroubleshootingAnalyzer validationTroubleshootingAnalyzer(
            List<ClassValidationOverride> mergedValidationOverrides,
            GeneratedClassMetadataCache generatedClassMetadataCache,
            ConstraintMergeService constraintMergeService
    ) {
        return new ValidationTroubleshootingAnalyzer(
                mergedValidationOverrides,
                generatedClassMetadataCache,
                constraintMergeService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
    public ExternalPayloadValidator externalPayloadValidator(Validator validator) {
        return new BeanValidationExternalPayloadValidator(validator);
    }

    @Bean
    @ConditionalOnProperty(name = "com.ampp.validation-enabled", havingValue = "true", matchIfMissing = true)
    public ValidationConfigurationCustomizer configDrivenValidationConfigurationCustomizer(
            ConfigDrivenConstraintMappingContributor contributor
    ) {
        AtomicBoolean warnedUnsupportedProvider = new AtomicBoolean();
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

    /**
     * Merges overrides from all contributors into a single list of class overrides.
     * When multiple contributors target the same class/field, the first contributor
     * (highest priority) wins for that field.
     */
    private static List<ClassValidationOverride> mergeContributorOverrides(
            List<ValidationOverrideContributor> contributors
    ) {
        Map<String, Map<String, FieldValidationOverride>> classIndex = new LinkedHashMap<>();
        for (ValidationOverrideContributor contributor : contributors) {
            for (ClassValidationOverride classOverride : contributor.getOverrides()) {
                if (classOverride.className() == null) {
                    continue;
                }
                Map<String, FieldValidationOverride> fieldIndex =
                    classIndex.computeIfAbsent(classOverride.className(), k -> new LinkedHashMap<>());
                for (FieldValidationOverride fieldOverride : classOverride.fields()) {
                    if (fieldOverride.fieldName() == null) {
                        continue;
                    }
                    fieldIndex.putIfAbsent(fieldOverride.fieldName(), fieldOverride);
                }
            }
        }
        List<ClassValidationOverride> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, FieldValidationOverride>> entry : classIndex.entrySet()) {
            result.add(new ClassValidationOverride(entry.getKey(), new ArrayList<>(entry.getValue().values())));
        }
        return result;
    }
}
