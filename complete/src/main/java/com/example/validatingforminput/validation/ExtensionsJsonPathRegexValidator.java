package com.example.validatingforminput.validation;

import java.util.ArrayList;
import java.util.List;

import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ExtensionsJsonPathRegexValidator implements ConstraintValidator<ExtensionsJsonPathRegex, Object> {

	private String jsonPath;

	private java.util.regex.Pattern compiledRegex;

	private JsonPath compiledJsonPath;

	@Override
	public void initialize(ExtensionsJsonPathRegex constraintAnnotation) {
		this.jsonPath = constraintAnnotation.jsonPath();
		try {
			this.compiledRegex = java.util.regex.Pattern.compile(constraintAnnotation.regex());
		}
		catch (java.util.regex.PatternSyntaxException exception) {
			throw new IllegalArgumentException("Invalid regex configured for ExtensionsJsonPathRegex: "
				+ constraintAnnotation.regex(), exception);
		}
		try {
			this.compiledJsonPath = JsonPath.compile(constraintAnnotation.jsonPath());
		}
		catch (InvalidPathException | IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid jsonPath configured for ExtensionsJsonPathRegex: "
				+ constraintAnnotation.jsonPath(), exception);
		}
	}

	@Override
	public boolean isValid(Object value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}

		List<Object> candidates;
		try {
			candidates = readCandidates(value);
		}
		catch (PathNotFoundException exception) {
			// jsonPath acts as a condition: if missing, there is nothing to validate.
			return true;
		}
		catch (RuntimeException exception) {
			return false;
		}

		for (Object candidate : candidates) {
			if (!matchesCandidate(candidate)) {
				return false;
			}
		}
		return true;
	}

	private List<Object> readCandidates(Object value) {
		Object result;
		if (value instanceof CharSequence textValue) {
			String raw = textValue.toString();
			if (raw.isBlank()) {
				return List.of();
			}
			result = JsonPath.parse(raw).read(jsonPath);
		}
		else {
			result = compiledJsonPath.read(value);
		}

		if (result == null) {
			return List.of();
		}
		if (result instanceof List<?> listResult) {
			return new ArrayList<>(listResult);
		}
		return List.of(result);
	}

	private boolean matchesCandidate(Object candidate) {
		if (candidate == null) {
			return true;
		}
		if (candidate instanceof CharSequence
			|| candidate instanceof Number
			|| candidate instanceof Boolean
			|| candidate instanceof Character
			|| candidate instanceof Enum<?>) {
			return compiledRegex.matcher(String.valueOf(candidate)).matches();
		}
		return false;
	}
}
