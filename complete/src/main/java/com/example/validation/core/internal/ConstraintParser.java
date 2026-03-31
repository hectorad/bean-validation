package com.example.validation.core.internal;

import java.math.BigDecimal;
import java.util.Locale;

final class ConstraintParser {

	private ConstraintParser() {
	}

	static ParsedConstraint parse(ValidationProperties.ConstraintMapping constraint) {
		if (constraint == null) {
			throw new IllegalArgumentException("constraint entry must not be null");
		}
		ConstraintType constraintType = ConstraintType.from(constraint.getConstraintType());
		ValidationProperties.ConstraintParameters params = constraint.getParams();
		String message = constraint.getMessage();
		return switch (constraintType) {
			case NOT_NULL -> ParsedConstraint.toggle(constraintType, message);
			case NOT_BLANK -> ParsedConstraint.toggle(constraintType, message);
			case MIN -> ParsedConstraint.numeric(constraintType, message, requiredLong(params.getValue(), "params.value"));
			case MAX -> ParsedConstraint.numeric(constraintType, message, requiredLong(params.getValue(), "params.value"));
			case DECIMAL_MIN -> ParsedConstraint.decimal(
				constraintType,
				message,
				requiredDecimal(params.getValue(), "params.value"),
				params.getInclusive());
			case DECIMAL_MAX -> ParsedConstraint.decimal(
				constraintType,
				message,
				requiredDecimal(params.getValue(), "params.value"),
				params.getInclusive());
			case SIZE -> ParsedConstraint.size(message, params.getMin(), params.getMax());
			case PATTERN -> ParsedConstraint.pattern(message, requiredText(params.getRegexp(), "Pattern requires params.regexp"));
			case EXTENSIONS -> ParsedConstraint.extensions(
				message,
				requiredText(params.getJsonPath(), "Extensions requires params.jsonPath"),
				requiredText(params.getRegexp(), "Extensions requires params.regexp"));
		};
	}

	private static BigDecimal requiredDecimal(BigDecimal value, String propertyName) {
		if (value == null) {
			throw new IllegalArgumentException(propertyName + " must be provided");
		}
		return value;
	}

	private static long requiredLong(BigDecimal value, String propertyName) {
		BigDecimal required = requiredDecimal(value, propertyName);
		try {
			return required.longValueExact();
		}
		catch (ArithmeticException exception) {
			throw new IllegalArgumentException(propertyName + " must be an integer", exception);
		}
	}

	private static String requiredText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	record ParsedConstraint(
		ConstraintType constraintType,
		String message,
		Long value,
		BigDecimal decimalValue,
		Boolean inclusive,
		Long min,
		Long max,
		String regexp,
		String jsonPath
	) {

		private static ParsedConstraint toggle(ConstraintType constraintType, String message) {
			return new ParsedConstraint(constraintType, message, null, null, null, null, null, null, null);
		}

		private static ParsedConstraint numeric(ConstraintType constraintType, String message, long value) {
			return new ParsedConstraint(constraintType, message, value, null, null, null, null, null, null);
		}

		private static ParsedConstraint decimal(
			ConstraintType constraintType,
			String message,
			BigDecimal decimalValue,
			Boolean inclusive
		) {
			return new ParsedConstraint(constraintType, message, null, decimalValue, inclusive, null, null, null, null);
		}

		private static ParsedConstraint size(String message, Long min, Long max) {
			if (min == null && max == null) {
				throw new IllegalArgumentException("Size requires params.min or params.max");
			}
			return new ParsedConstraint(ConstraintType.SIZE, message, null, null, null, min, max, null, null);
		}

		private static ParsedConstraint pattern(String message, String regexp) {
			return new ParsedConstraint(ConstraintType.PATTERN, message, null, null, null, null, null, regexp, null);
		}

		private static ParsedConstraint extensions(String message, String jsonPath, String regexp) {
			return new ParsedConstraint(ConstraintType.EXTENSIONS, message, null, null, null, null, null, regexp, jsonPath);
		}
	}

	enum ConstraintType {
		NOT_NULL,
		NOT_BLANK,
		MIN,
		MAX,
		DECIMAL_MIN,
		DECIMAL_MAX,
		SIZE,
		PATTERN,
		EXTENSIONS;

		private static ConstraintType from(String rawValue) {
			String normalized = normalize(rawValue);
			return switch (normalized) {
				case "notnull" -> NOT_NULL;
				case "notblank" -> NOT_BLANK;
				case "min" -> MIN;
				case "max" -> MAX;
				case "decimalmin" -> DECIMAL_MIN;
				case "decimalmax" -> DECIMAL_MAX;
				case "size" -> SIZE;
				case "pattern" -> PATTERN;
				case "extensions" -> EXTENSIONS;
				default -> throw new IllegalArgumentException("Unsupported constraintType: " + rawValue);
			};
		}

		private static String normalize(String rawValue) {
			if (rawValue == null) {
				return "";
			}
			return rawValue
				.replace("-", "")
				.replace("_", "")
				.replace(" ", "")
				.toLowerCase(Locale.ROOT);
		}
	}
}
