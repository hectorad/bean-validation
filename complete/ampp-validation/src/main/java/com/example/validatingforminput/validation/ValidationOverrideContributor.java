package com.example.validatingforminput.validation;

import java.util.List;

import org.springframework.core.Ordered;

public interface ValidationOverrideContributor extends Ordered {

    List<ClassValidationOverride> getOverrides();
}
