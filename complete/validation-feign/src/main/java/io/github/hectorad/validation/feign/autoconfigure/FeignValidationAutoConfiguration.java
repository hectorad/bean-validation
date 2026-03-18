package io.github.hectorad.validation.feign.autoconfigure;

import io.github.hectorad.validation.ExternalPayloadValidator;
import io.github.hectorad.validation.feign.ValidatingFeignCapability;
import feign.Capability;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = "io.github.hectorad.validation.autoconfigure.ValidationAutoConfiguration")
@ConditionalOnClass(name = {
    "feign.Capability",
    "org.springframework.cloud.openfeign.FeignClientFactoryBean"
})
@ConditionalOnBean(ExternalPayloadValidator.class)
@ConditionalOnProperty(prefix = "hector.validation.feign", name = "enabled", havingValue = "true")
public class FeignValidationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ValidatingFeignCapability.class)
    public Capability validatingFeignCapability(ExternalPayloadValidator externalPayloadValidator) {
        return new ValidatingFeignCapability(externalPayloadValidator);
    }
}
