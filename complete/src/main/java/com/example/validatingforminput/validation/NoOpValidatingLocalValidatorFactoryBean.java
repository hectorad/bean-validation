package com.example.validatingforminput.validation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

import org.springframework.validation.Errors;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.executable.ExecutableValidator;

public class NoOpValidatingLocalValidatorFactoryBean extends LocalValidatorFactoryBean {

	@Override
	public void afterPropertiesSet() {
		// Skip Hibernate Validator factory initialization — not needed for a no-op validator
	}

	@Override
	public void validate(Object target, Errors errors) {
		// no-op: Spring MVC DataBinder calls this method
	}

	@Override
	public void validate(Object target, Errors errors, Object... validationHints) {
		// no-op: Spring MVC SmartValidator calls this method
	}

	@Override
	public <T> Set<ConstraintViolation<T>> validate(T object, Class<?>... groups) {
		return Collections.emptySet();
	}

	@Override
	public <T> Set<ConstraintViolation<T>> validateProperty(T object, String propertyName, Class<?>... groups) {
		return Collections.emptySet();
	}

	@Override
	public <T> Set<ConstraintViolation<T>> validateValue(
		Class<T> beanType, String propertyName, Object value, Class<?>... groups
	) {
		return Collections.emptySet();
	}

	@Override
	public ExecutableValidator forExecutables() {
		return NoOpExecutableValidator.INSTANCE;
	}

	private enum NoOpExecutableValidator implements ExecutableValidator {

		INSTANCE;

		@Override
		public <T> Set<ConstraintViolation<T>> validateParameters(
			T object, Method method, Object[] parameterValues, Class<?>... groups
		) {
			return Collections.emptySet();
		}

		@Override
		public <T> Set<ConstraintViolation<T>> validateReturnValue(
			T object, Method method, Object returnValue, Class<?>... groups
		) {
			return Collections.emptySet();
		}

		@Override
		public <T> Set<ConstraintViolation<T>> validateConstructorParameters(
			Constructor<? extends T> constructor, Object[] parameterValues, Class<?>... groups
		) {
			return Collections.emptySet();
		}

		@Override
		public <T> Set<ConstraintViolation<T>> validateConstructorReturnValue(
			Constructor<? extends T> constructor, T createdObject, Class<?>... groups
		) {
			return Collections.emptySet();
		}
	}
}
