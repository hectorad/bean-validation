package com.example.validation.core.internal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import org.springframework.validation.Errors;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.executable.ExecutableValidator;

public class RequestAwareValidatingLocalValidatorFactoryBean extends LocalValidatorFactoryBean {

    private final ValidationProperties validationProperties;

    private final InheritedFieldOverrideValidationProcessor inheritedFieldOverrideValidationProcessor;

    private final ExecutableValidator executableValidator = new RequestAwareExecutableValidator();

    public RequestAwareValidatingLocalValidatorFactoryBean(ValidationProperties validationProperties) {
        this(validationProperties, null);
    }

    public RequestAwareValidatingLocalValidatorFactoryBean(
        ValidationProperties validationProperties,
        InheritedFieldOverrideValidationProcessor inheritedFieldOverrideValidationProcessor
    ) {
        this.validationProperties = validationProperties;
        this.inheritedFieldOverrideValidationProcessor = inheritedFieldOverrideValidationProcessor;
    }

    @Override
    public void validate(Object target, Errors errors) {
        if (shouldBypassValidation()) {
            return;
        }
        if (!shouldProcessInheritedOverrides()) {
            super.validate(target, errors);
            return;
        }
        processConstraintViolations(validateAndProcess(target), errors);
    }

    @Override
    public void validate(Object target, Errors errors, Object... validationHints) {
        if (shouldBypassValidation()) {
            return;
        }
        if (!shouldProcessInheritedOverrides()) {
            super.validate(target, errors, validationHints);
            return;
        }
        processConstraintViolations(validateAndProcess(target, validationGroups(validationHints)), errors);
    }

    @Override
    public <T> Set<ConstraintViolation<T>> validate(T object, Class<?>... groups) {
        if (shouldBypassValidation()) {
            return Collections.emptySet();
        }
        Set<ConstraintViolation<T>> violations = super.validate(object, groups);
        if (!shouldProcessInheritedOverrides()) {
            return violations;
        }
        return inheritedFieldOverrideValidationProcessor.process(object, violations, groups);
    }

    @Override
    public <T> Set<ConstraintViolation<T>> validateProperty(T object, String propertyName, Class<?>... groups) {
        if (shouldBypassValidation()) {
            return Collections.emptySet();
        }
        return super.validateProperty(object, propertyName, groups);
    }

    @Override
    public <T> Set<ConstraintViolation<T>> validateValue(
        Class<T> beanType, String propertyName, Object value, Class<?>... groups
    ) {
        if (shouldBypassValidation()) {
            return Collections.emptySet();
        }
        return super.validateValue(beanType, propertyName, value, groups);
    }

    @Override
    public ExecutableValidator forExecutables() {
        return executableValidator;
    }

    boolean shouldBypassValidation() {
        if (!validationProperties.isValidationEnabled()) {
            return true;
        }

        ValidationProperties.RequestValidationBypass requestValidationBypass = validationProperties.getRequestValidationBypass();
        if (!requestValidationBypass.isEnabled()) {
            return false;
        }

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return false;
        }

        HttpServletRequest request = servletRequestAttributes.getRequest();
        if (request == null) {
            return false;
        }

        Enumeration<String> headerValues = request.getHeaders(requestValidationBypass.getHeaderName());
        while (headerValues != null && headerValues.hasMoreElements()) {
            if (requestValidationBypass.getHeaderValue().equals(headerValues.nextElement())) {
                return true;
            }
        }

        return false;
    }

    private boolean shouldProcessInheritedOverrides() {
        return inheritedFieldOverrideValidationProcessor != null
            && inheritedFieldOverrideValidationProcessor.hasOverrides();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Set<ConstraintViolation<Object>> validateAndProcess(Object target, Class<?>... groups) {
        return (Set) validate(target, groups);
    }

    private Class<?>[] validationGroups(Object... validationHints) {
        if (validationHints == null || validationHints.length == 0) {
            return new Class<?>[0];
        }
        List<Class<?>> groups = new ArrayList<>();
        for (Object hint : validationHints) {
            if (hint instanceof Class<?> group) {
                groups.add(group);
            }
            else if (hint instanceof Class<?>[] groupArray) {
                Collections.addAll(groups, groupArray);
            }
        }
        return groups.toArray(Class<?>[]::new);
    }

    private ExecutableValidator delegateExecutableValidator() {
        return RequestAwareValidatingLocalValidatorFactoryBean.super.forExecutables();
    }

    private final class RequestAwareExecutableValidator implements ExecutableValidator {

        @Override
        public <T> Set<ConstraintViolation<T>> validateParameters(
            T object, Method method, Object[] parameterValues, Class<?>... groups
        ) {
            if (shouldBypassValidation()) {
                return Collections.emptySet();
            }
            return delegateExecutableValidator().validateParameters(object, method, parameterValues, groups);
        }

        @Override
        public <T> Set<ConstraintViolation<T>> validateReturnValue(
            T object, Method method, Object returnValue, Class<?>... groups
        ) {
            if (shouldBypassValidation()) {
                return Collections.emptySet();
            }
            return delegateExecutableValidator().validateReturnValue(object, method, returnValue, groups);
        }

        @Override
        public <T> Set<ConstraintViolation<T>> validateConstructorParameters(
            Constructor<? extends T> constructor, Object[] parameterValues, Class<?>... groups
        ) {
            if (shouldBypassValidation()) {
                return Collections.emptySet();
            }
            return delegateExecutableValidator().validateConstructorParameters(constructor, parameterValues, groups);
        }

        @Override
        public <T> Set<ConstraintViolation<T>> validateConstructorReturnValue(
            Constructor<? extends T> constructor, T createdObject, Class<?>... groups
        ) {
            if (shouldBypassValidation()) {
                return Collections.emptySet();
            }
            return delegateExecutableValidator().validateConstructorReturnValue(constructor, createdObject, groups);
        }
    }
}
