package com.example.validation.core.api;

public sealed interface ValidationRule permits
	NotNullRule,
	NotBlankRule,
	LowerBoundRule,
	UpperBoundRule,
	SizeRule,
	PatternRule,
	JsonPathRegexRule {
}
