package com.example.validatingforminput;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;

@Service
@Validated
public class PersonValidationService {

	public void validate(@Valid PersonForm personForm) {
		// Method-level validation is handled by AOP and the configured validator.
	}
}
