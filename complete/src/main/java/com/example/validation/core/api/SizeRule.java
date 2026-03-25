package com.example.validation.core.api;

public record SizeRule(Integer min, Integer max, String minMessage, String maxMessage) implements ValidationRule {

	public SizeRule {
		if (min == null && max == null) {
			throw new IllegalArgumentException("Size rule requires a min, a max, or both.");
		}
	}
}
