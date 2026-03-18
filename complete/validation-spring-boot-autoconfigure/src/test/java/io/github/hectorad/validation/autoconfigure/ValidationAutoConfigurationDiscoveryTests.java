package io.github.hectorad.validation.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hectorad.validation.ConstraintMergeService;
import io.github.hectorad.validation.ExternalPayloadValidator;
import io.github.hectorad.validation.hibernate.ConfigDrivenConstraintMappingContributor;
import io.github.hectorad.validation.hibernate.GeneratedClassMetadataCache;
import jakarta.validation.Configuration;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
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
            assertThat(context.getBeansOfType(ConfigDrivenConstraintMappingContributor.class)).hasSize(1);
            assertThat(context.getBeansOfType(ConstraintMergeService.class)).hasSize(1);
            assertThat(context.getBeansOfType(GeneratedClassMetadataCache.class)).hasSize(1);
            assertThat(context.getBeansOfType(ExternalPayloadValidator.class)).hasSize(1);
            assertThat(context.getBean("defaultValidator", LocalValidatorFactoryBean.class))
                .isInstanceOf(FrameworkLocalValidatorFactoryBean.class);
        }
    }

    @Test
    void shouldProvideSameValidatorTypeWhenValidationIsDisabled() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestApplication.class)
            .web(WebApplicationType.NONE)
            .run("--hector.validation.enabled=false")) {
            LocalValidatorFactoryBean validator = context.getBean("defaultValidator", LocalValidatorFactoryBean.class);

            assertThat(validator).isInstanceOf(FrameworkLocalValidatorFactoryBean.class);
            assertThat(context.getBeansOfType(ConstraintMergeService.class)).isEmpty();
            assertThat(context.getBeansOfType(GeneratedClassMetadataCache.class)).isEmpty();
            assertThat(ReflectionTestUtils.getField(validator, "validatorFactory")).isNotNull();
            assertThat(validator.usingContext()).isNotNull();
            assertThat(validator.unwrap(jakarta.validation.ValidatorFactory.class)).isNotNull();
        }
    }

    @Test
    void shouldBackOffToUserProvidedConstraintMergeService() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(CustomConstraintMergeServiceApplication.class)
            .web(WebApplicationType.NONE)
            .run()) {
            assertThat(context.getBeansOfType(ConstraintMergeService.class)).hasSize(1);
            Object contributor = context.getBean(ConfigDrivenConstraintMappingContributor.class);
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

            assertThat(validator).isNotInstanceOf(FrameworkLocalValidatorFactoryBean.class);
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
                    "hector.validation.http-bypass.enabled=true",
                    "hector.validation.overrides[0].class-name=",
                    "hector.validation.overrides[0].fields[0].field-name=name",
                    "hector.validation.overrides[0].fields[0].constraints.not-null.value=true")
                .run()) {
                assertThat(context).isNotNull();
            }
        })
            .hasStackTraceContaining("overrides[0].className")
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
            LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
            validator.setMessageInterpolator(new ParameterMessageInterpolator());
            return validator;
        }
    }
}
