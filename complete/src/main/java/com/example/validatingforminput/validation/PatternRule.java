package com.example.validatingforminput.validation;

import java.util.EnumSet;
import java.util.Set;

import jakarta.validation.constraints.Pattern;

public record PatternRule(String regex, Set<Pattern.Flag> flags) {

	public PatternRule {
		if (flags == null || flags.isEmpty()) {
			flags = EnumSet.noneOf(Pattern.Flag.class);
		}
		else {
			flags = EnumSet.copyOf(flags);
		}
	}
}
