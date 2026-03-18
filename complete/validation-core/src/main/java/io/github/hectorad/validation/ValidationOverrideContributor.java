package io.github.hectorad.validation;

import java.util.List;

public interface ValidationOverrideContributor {

    List<ClassValidationOverride> getValidationOverrides();
}
