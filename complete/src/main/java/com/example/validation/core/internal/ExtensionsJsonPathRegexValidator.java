package com.example.validation.core.internal;

import com.example.validation.core.api.ExtensionsJsonPathRegex;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ExtensionsJsonPathRegexValidator implements ConstraintValidator<ExtensionsJsonPathRegex, Object> {

	private Pattern compiledRegex;

	private JsonPath compiledJsonPath;

	@Override
	public void initialize(ExtensionsJsonPathRegex constraintAnnotation) {
		this.compiledRegex = compileRegex(constraintAnnotation.regex());
		this.compiledJsonPath = compileJsonPath(constraintAnnotation.jsonPath());
	}

	@Override
	public boolean isValid(Object value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}

		try {
			for (Object candidate : extractCandidates(value)) {
				if (!matchesCandidate(candidate)) {
					return false;
				}
			}
			return true;
		}
		catch (PathNotFoundException exception) {
			// jsonPath acts as a condition: if missing, there is nothing to validate.
			return true;
		}
		catch (com.jayway.jsonpath.JsonPathException exception) {
			return false;
		}
	}

	private Pattern compileRegex(String regex) {
		try {
			return Pattern.compile(regex);
		}
		catch (PatternSyntaxException exception) {
			throw new IllegalArgumentException("Invalid regex configured for ExtensionsJsonPathRegex: " + regex, exception);
		}
	}

	private JsonPath compileJsonPath(String jsonPath) {
		try {
			return JsonPath.compile(jsonPath);
		}
		catch (InvalidPathException | IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid jsonPath configured for ExtensionsJsonPathRegex: " + jsonPath, exception);
		}
	}

	private List<?> extractCandidates(Object value) {
		Object resolvedValue = readJsonPath(value);
		if (resolvedValue == null) {
			return List.of();
		}
		if (resolvedValue instanceof List<?> listValue) {
			return listValue;
		}
		return List.of(resolvedValue);
	}

	private Object readJsonPath(Object value) {
		if (value instanceof CharSequence textValue) {
			String raw = textValue.toString();
			if (raw.isBlank()) {
				return null;
			}
			return parseJson(raw).read(compiledJsonPath);
		}
		return compiledJsonPath.read(value);
	}

	private ReadContext parseJson(String raw) {
		return JsonPath.parse(raw);
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
