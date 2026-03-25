package com.example.validation.core.spi;

import java.util.Optional;

public interface FieldConstraintContributor {

	Optional<ConstraintContribution> contribute(ValidationFieldContext fieldContext);
}
