package com.example.validatingforminput.validation;

public record ExtensionRegexRule(String jsonPath, String regex, String message) {

	public ExtensionRegexRule(String jsonPath, String regex) {
		this(jsonPath, regex, null);
	}
}
