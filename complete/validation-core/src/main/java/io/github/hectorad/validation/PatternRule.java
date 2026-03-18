package io.github.hectorad.validation;

import java.util.EnumSet;
import java.util.Set;

import jakarta.validation.constraints.Pattern;

public record PatternRule(String regex, Set<Pattern.Flag> flags, String message) {

	public PatternRule(String regex, Set<Pattern.Flag> flags) {
		this(regex, flags, null);
	}

	public PatternRule {
		if (flags == null || flags.isEmpty()) {
			flags = EnumSet.noneOf(Pattern.Flag.class);
		}
		else {
			flags = EnumSet.copyOf(flags);
		}
	}
}
