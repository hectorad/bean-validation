package com.example.validatingforminput.validation.feign;

import feign.Capability;
import feign.codec.Decoder;
import jakarta.validation.Validator;

public class ValidatingFeignCapability implements Capability {

    private final Validator validator;

    public ValidatingFeignCapability(Validator validator) {
        this.validator = validator;
    }

    @Override
    public Decoder enrich(Decoder decoder) {
        if (decoder instanceof ValidatingFeignDecoder) {
            return decoder;
        }
        return new ValidatingFeignDecoder(decoder, validator);
    }
}
