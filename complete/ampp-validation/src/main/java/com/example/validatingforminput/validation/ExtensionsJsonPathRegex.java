package com.example.validatingforminput.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = ExtensionsJsonPathRegexValidator.class)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ExtensionsJsonPathRegex.List.class)
public @interface ExtensionsJsonPathRegex {

	String message() default "JSONPath '{jsonPath}' value must match regex '{regex}'";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	String jsonPath();

	String regex();

	@Documented
	@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE })
	@Retention(RetentionPolicy.RUNTIME)
	@interface List {

		ExtensionsJsonPathRegex[] value();
	}
}
