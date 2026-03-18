package io.github.hectorad.validation.autoconfigure;

import java.util.concurrent.atomic.AtomicBoolean;

import io.github.hectorad.validation.BeanValidationExternalPayloadValidator;
import io.github.hectorad.validation.ConstraintMergeService;
import io.github.hectorad.validation.ExternalPayloadValidator;
import io.github.hectorad.validation.ValidationOverrideContributor;
import io.github.hectorad.validation.hibernate.ConfigDrivenConstraintMappingContributor;
import io.github.hectorad.validation.hibernate.GeneratedClassMetadataCache;
import io.github.hectorad.validation.hibernate.ValidationOverrideRegistry;
import io.github.hectorad.validation.hibernate.ValidationTroubleshootingAnalyzer;
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
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.ServletRequestAttributes;

@AutoConfiguration(before = org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration.class)
@ConditionalOnClass({ValidationConfigurationCustomizer.class, HibernateValidatorConfiguration.class})
@EnableConfigurationProperties(ValidationProperties.class)
public class ValidationAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ValidationAutoConfiguration.class);

    @Bean(name = "defaultValidator")
    @Primary
    @ConditionalOnMissingBean(name = "defaultValidator")
    public LocalValidatorFactoryBean defaultValidator(
        ApplicationContext applicationContext,
        ObjectProvider<ValidationConfigurationCustomizer> customizers,
        ObjectProvider<ValidationBypassStrategy> bypassStrategies,
        ValidationProperties validationProperties
    ) {
        if (!validationProperties.isEnabled()) {
            log.warn("*** ALL VALIDATION IS DISABLED (hector.validation.enabled=false). No constraints will be enforced on any field. ***");
        }
        FrameworkLocalValidatorFactoryBean factoryBean = new FrameworkLocalValidatorFactoryBean(
            validationProperties.isEnabled(),
            bypassStrategies.orderedStream().toList());
        factoryBean.setConfigurationInitializer(configuration ->
            customizers.orderedStream().forEach(customizer -> customizer.customize(configuration)));
        factoryBean.setMessageInterpolator(new MessageInterpolatorFactory(applicationContext).getObject());
        return factoryBean;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "hector.validation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ConstraintMergeService constraintMergeService() {
        return new ConstraintMergeService();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "hector.validation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ValidationOverrideContributor propertiesValidationOverrideContributor(ValidationProperties validationProperties) {
        return new PropertiesValidationOverrideContributor(validationProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "hector.validation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ValidationOverrideRegistry validationOverrideRegistry(
        ObjectProvider<ValidationOverrideContributor> validationOverrideContributors
    ) {
        return new ValidationOverrideRegistry(validationOverrideContributors.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "hector.validation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public GeneratedClassMetadataCache generatedClassMetadataCache(
        ValidationOverrideRegistry validationOverrideRegistry,
        ValidationProperties validationProperties
    ) {
        return new GeneratedClassMetadataCache(validationOverrideRegistry, validationProperties.isFailOnError());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "hector.validation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ConfigDrivenConstraintMappingContributor configDrivenConstraintMappingContributor(
        ValidationOverrideRegistry validationOverrideRegistry,
        GeneratedClassMetadataCache generatedClassMetadataCache,
        ConstraintMergeService constraintMergeService,
        ValidationProperties validationProperties
    ) {
        return new ConfigDrivenConstraintMappingContributor(
            validationOverrideRegistry,
            generatedClassMetadataCache,
            constraintMergeService,
            validationProperties.isFailOnError());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "hector.validation", name = "enabled", havingValue = "true", matchIfMissing = true)
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
    @ConditionalOnProperty(prefix = "hector.validation", name = "enabled", havingValue = "true", matchIfMissing = true)
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

    @Bean
    @ConditionalOnClass({ServletRequestAttributes.class, HttpServletRequest.class})
    @ConditionalOnProperty(prefix = "hector.validation.http-bypass", name = "enabled", havingValue = "true")
    public ValidationBypassStrategy servletHeaderValidationBypassStrategy(ValidationProperties validationProperties) {
        return new ServletHeaderValidationBypassStrategy(validationProperties.getHttpBypass());
    }
}
