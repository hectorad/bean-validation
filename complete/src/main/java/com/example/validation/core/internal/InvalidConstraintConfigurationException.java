package com.example.validation.core.internal;

public class InvalidConstraintConfigurationException extends RuntimeException {

	public InvalidConstraintConfigurationException(String message) {
		super(message);
	}

	public InvalidConstraintConfigurationException(String message, Throwable cause) {
		super(message, cause);
	}
}
