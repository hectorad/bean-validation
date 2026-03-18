package io.github.hectorad.validation;

import java.util.List;

public record EffectiveFieldConstraints(
	boolean notNull,
	String notNullMessage,
	boolean notBlank,
	String notBlankMessage,
	NumericBound min,
	String minMessage,
	NumericBound max,
	String maxMessage,
	Integer sizeMin,
	String sizeMinMessage,
	Integer sizeMax,
	String sizeMaxMessage,
	List<PatternRule> patterns,
	List<ExtensionRegexRule> extensionRules
) {

	public EffectiveFieldConstraints {
		patterns = (patterns == null) ? List.of() : List.copyOf(patterns);
		extensionRules = (extensionRules == null) ? List.of() : List.copyOf(extensionRules);
	}
}
