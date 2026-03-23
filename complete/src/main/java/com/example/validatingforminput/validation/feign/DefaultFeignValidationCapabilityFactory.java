package com.example.validatingforminput.validation.feign;

import feign.Capability;
import jakarta.validation.Validator;

public class DefaultFeignValidationCapabilityFactory implements FeignValidationCapabilityFactory {

    private final Validator validator;

    public DefaultFeignValidationCapabilityFactory(Validator validator) {
        this.validator = validator;
    }

    @Override
    public Capability create() {
        return new ValidatingFeignCapability(validator);
    }
}
