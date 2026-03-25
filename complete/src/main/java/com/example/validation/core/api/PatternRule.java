package com.example.validation.core.api;

public record PatternRule(String regex, String message) implements ValidationRule {

	public PatternRule(String regex) {
		this(regex, null);
	}
}
