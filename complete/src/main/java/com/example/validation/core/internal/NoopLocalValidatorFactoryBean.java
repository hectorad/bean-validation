package com.example.validation.core.internal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

import org.springframework.validation.Errors;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.executable.ExecutableValidator;

public class NoopLocalValidatorFactoryBean extends LocalValidatorFactoryBean {

    private final ExecutableValidator executableValidator = new NoopExecutableValidator();

    @Override
    public void afterPropertiesSet() {
        // Skip Hibernate Validator bootstrap entirely
    }

    @Override
    public void destroy() {
        // Nothing to close since no ValidatorFactory was created
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return true;
    }

    @Override
    public void validate(Object target, Errors errors) {
        // No-op
    }

    @Override
    public void validate(Object target, Errors errors, Object... validationHints) {
        // No-op
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
        return executableValidator;
    }

    private static final class NoopExecutableValidator implements ExecutableValidator {

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
