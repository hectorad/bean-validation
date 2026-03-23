package com.example.validatingforminput.validation;

public record PatternRule(String regex, String message) {

	public PatternRule(String regex) {
		this(regex, null);
	}
}
