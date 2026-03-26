package com.example.validation.feign.internal;

import com.example.validation.core.api.ExternalPayloadValidator;
import com.example.validation.feign.spi.FeignValidationCapabilityFactory;

import feign.Capability;

public class DefaultFeignValidationCapabilityFactory implements FeignValidationCapabilityFactory {

    private final ExternalPayloadValidator externalPayloadValidator;

    public DefaultFeignValidationCapabilityFactory(ExternalPayloadValidator externalPayloadValidator) {
        this.externalPayloadValidator = externalPayloadValidator;
    }

    @Override
    public Capability create() {
        return new ValidatingFeignCapability(externalPayloadValidator);
    }
}
