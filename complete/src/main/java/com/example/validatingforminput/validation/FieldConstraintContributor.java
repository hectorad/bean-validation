package com.example.validatingforminput.validation;

import java.util.Optional;

/**
 * Supplies configured constraints for a specific class field.
 */
public interface FieldConstraintContributor {

	Optional<ValidationProperties.Constraints> contribute(
		String className,
		String fieldName,
		BaselineFieldConstraints baseline
	);
}
