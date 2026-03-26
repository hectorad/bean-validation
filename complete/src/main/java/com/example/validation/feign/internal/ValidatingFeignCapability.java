package com.example.validation.feign.internal;

import com.example.validation.core.api.ExternalPayloadValidator;

import feign.Capability;
import feign.codec.Decoder;

public class ValidatingFeignCapability implements Capability {

    private final ExternalPayloadValidator externalPayloadValidator;

    public ValidatingFeignCapability(ExternalPayloadValidator externalPayloadValidator) {
        this.externalPayloadValidator = externalPayloadValidator;
    }

    @Override
    public Decoder enrich(Decoder decoder) {
        if (decoder instanceof ValidatingFeignDecoder) {
            return decoder;
        }
        return new ValidatingFeignDecoder(decoder, externalPayloadValidator);
    }
}
