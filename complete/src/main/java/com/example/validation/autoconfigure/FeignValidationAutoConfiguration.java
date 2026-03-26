package com.example.validation.autoconfigure;

import com.example.validation.core.api.ExternalPayloadValidator;
import com.example.validation.feign.internal.DefaultFeignValidationCapabilityFactory;
import com.example.validation.feign.spi.FeignValidationCapabilityFactory;
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
    @ConditionalOnMissingBean(FeignValidationCapabilityFactory.class)
    public FeignValidationCapabilityFactory feignValidationCapabilityFactory(ExternalPayloadValidator externalPayloadValidator) {
        return new DefaultFeignValidationCapabilityFactory(externalPayloadValidator);
    }
}
