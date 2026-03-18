package com.example.validation.autoconfigure;

import com.example.validatingforminput.validation.ExternalPayloadValidator;
import com.example.validatingforminput.validation.feign.ValidatingFeignCapability;
import feign.Capability;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = ValidationAutoConfiguration.class)
@ConditionalOnClass(name = {
    "feign.Capability",
    "org.springframework.cloud.openfeign.FeignClientFactoryBean"
})
@ConditionalOnBean(ExternalPayloadValidator.class)
@ConditionalOnProperty(prefix = "com.ampp.feign-response-validation", name = "enabled", havingValue = "true")
public class FeignValidationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ValidatingFeignCapability.class)
    public Capability validatingFeignCapability(ExternalPayloadValidator externalPayloadValidator) {
        return new ValidatingFeignCapability(externalPayloadValidator);
    }
}
