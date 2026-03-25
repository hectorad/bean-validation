package com.example.validation.feign.spi;

import feign.Capability;

public interface FeignValidationCapabilityFactory {

    Capability create();
}
