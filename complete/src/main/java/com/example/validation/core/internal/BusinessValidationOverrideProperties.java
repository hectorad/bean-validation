package com.example.validation.core.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "com.ampp")
public class BusinessValidationOverrideProperties {

    @Valid
    private List<ValidationProperties.@NotNull @Valid ClassMapping> businessValidationOverride = new ArrayList<>();

    public List<ValidationProperties.ClassMapping> getBusinessValidationOverride() {
        return businessValidationOverride;
    }

    public void setBusinessValidationOverride(List<ValidationProperties.ClassMapping> businessValidationOverride) {
        this.businessValidationOverride =
                (businessValidationOverride == null) ? new ArrayList<>() : new ArrayList<>(businessValidationOverride);
    }
}
