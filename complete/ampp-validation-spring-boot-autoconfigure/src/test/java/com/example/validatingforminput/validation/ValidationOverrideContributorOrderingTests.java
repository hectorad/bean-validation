package com.example.validatingforminput.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.example.validation.autoconfigure.PropertiesValidationOverrideContributor;
import com.example.validation.autoconfigure.ValidationAutoConfiguration;

class ValidationOverrideContributorOrderingTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class));

    @Test
    void propertiesContributorHasLowestPrecedence() {
        contextRunner
            .withPropertyValues("com.ampp.validation-enabled=true")
            .run(context -> {
                PropertiesValidationOverrideContributor contributor =
                    context.getBean(PropertiesValidationOverrideContributor.class);
                assertThat(contributor.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
            });
    }

    @Test
    void programmaticContributorWinsOverPropertiesContributor() {
        contextRunner
            .withUserConfiguration(HighPriorityContributorConfiguration.class)
            .withPropertyValues(
                "com.ampp.validation-enabled=true",
                "com.ampp.business-validation-override[0].full-class-name=com.example.validatingforminput.validation.ValidationOverrideContributorOrderingTests$TestTarget",
                "com.ampp.business-validation-override[0].fields[0].field-name=value",
                "com.ampp.business-validation-override[0].fields[0].constraints.not-null.value=true",
                "com.ampp.business-validation-override[0].fields[0].constraints.not-null.message=From YAML")
            .run(context -> {
                ConfigDrivenConstraintMappingContributor mappingContributor =
                    context.getBean(ConfigDrivenConstraintMappingContributor.class);
                assertThat(mappingContributor).isNotNull();

                // Verify both contributors are registered
                List<ValidationOverrideContributor> contributors =
                    context.getBeanProvider(ValidationOverrideContributor.class).orderedStream().toList();
                assertThat(contributors).hasSize(2);
                // High-priority contributor comes first
                assertThat(contributors.get(0)).isInstanceOf(TestHighPriorityContributor.class);
                assertThat(contributors.get(1)).isInstanceOf(PropertiesValidationOverrideContributor.class);
            });
    }

    @Test
    void multipleContributorsAreOrderedBySpringOrdering() {
        contextRunner
            .withUserConfiguration(MultipleContributorsConfiguration.class)
            .withPropertyValues("com.ampp.validation-enabled=true")
            .run(context -> {
                List<ValidationOverrideContributor> contributors =
                    context.getBeanProvider(ValidationOverrideContributor.class).orderedStream().toList();
                assertThat(contributors).hasSize(3);
                assertThat(contributors.get(0)).isInstanceOf(TestHighPriorityContributor.class);
                assertThat(contributors.get(1)).isInstanceOf(TestMediumPriorityContributor.class);
                assertThat(contributors.get(2)).isInstanceOf(PropertiesValidationOverrideContributor.class);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class HighPriorityContributorConfiguration {

        @Bean
        TestHighPriorityContributor testHighPriorityContributor() {
            return new TestHighPriorityContributor();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MultipleContributorsConfiguration {

        @Bean
        TestHighPriorityContributor testHighPriorityContributor() {
            return new TestHighPriorityContributor();
        }

        @Bean
        TestMediumPriorityContributor testMediumPriorityContributor() {
            return new TestMediumPriorityContributor();
        }
    }

    static class TestHighPriorityContributor implements ValidationOverrideContributor {

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public List<ClassValidationOverride> getOverrides() {
            return List.of(new ClassValidationOverride(
                TestTarget.class.getName(),
                List.of(new FieldValidationOverride("value", new ConstraintOverrideSet(
                    new ConstraintOverrideSet.BooleanOverride(true, "From programmatic contributor"),
                    null, null, null, null, null, null, null, null)))));
        }
    }

    static class TestMediumPriorityContributor implements ValidationOverrideContributor {

        @Override
        public int getOrder() {
            return 0;
        }

        @Override
        public List<ClassValidationOverride> getOverrides() {
            return List.of();
        }
    }

    @SuppressWarnings("unused")
    static class TestTarget {
        private String value;
    }
}
