package com.example.validation.core.api;

public record JsonPathRegexRule(String jsonPath, String regex, String message) implements ValidationRule {

	public JsonPathRegexRule(String jsonPath, String regex) {
		this(jsonPath, regex, null);
	}
}
