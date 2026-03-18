package io.github.hectorad.validation.autoconfigure;

import java.util.List;

import io.github.hectorad.validation.ClassValidationOverride;
import io.github.hectorad.validation.ValidationOverrideContributor;
import org.springframework.core.Ordered;

public class PropertiesValidationOverrideContributor implements ValidationOverrideContributor, Ordered {

    private final ValidationProperties validationProperties;

    public PropertiesValidationOverrideContributor(ValidationProperties validationProperties) {
        this.validationProperties = validationProperties;
    }

    @Override
    public List<ClassValidationOverride> getValidationOverrides() {
        return validationProperties.toValidationOverrides();
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
