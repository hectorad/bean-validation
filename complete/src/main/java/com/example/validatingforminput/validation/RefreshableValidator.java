package com.example.validatingforminput.validation;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.SmartValidator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.executable.ExecutableValidator;
import jakarta.validation.metadata.BeanDescriptor;

@Component
public class RefreshableValidator implements SmartValidator, Validator, DisposableBean {

	private static final Logger LOGGER = LoggerFactory.getLogger(RefreshableValidator.class);

	private final Supplier<LocalValidatorFactoryBean> validatorFactorySupplier;

	private final AtomicReference<LocalValidatorFactoryBean> delegate;

	private final ReentrantLock refreshLock = new ReentrantLock();

	public RefreshableValidator(Supplier<LocalValidatorFactoryBean> validatorFactorySupplier) {
		this.validatorFactorySupplier = validatorFactorySupplier;
		this.delegate = new AtomicReference<>(validatorFactorySupplier.get());
	}

	@EventListener(condition = "#root.event.class.name == 'org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent'")
	public void onRefreshScopeRefreshed(Object event) {
		refresh();
	}

	public void refresh() {
		refreshLock.lock();
		try {
			LocalValidatorFactoryBean currentValidator = delegate.get();
			LocalValidatorFactoryBean newValidator = validatorFactorySupplier.get();
			delegate.set(newValidator);
			currentValidator.destroy();
			LOGGER.info("validation_factory_refresh outcome=success");
		}
		catch (Exception exception) {
			LOGGER.error("validation_factory_refresh outcome=failure reason={}", exception.getMessage(), exception);
		}
		finally {
			refreshLock.unlock();
		}
	}

	@Override
	public boolean supports(Class<?> clazz) {
		return delegate.get().supports(clazz);
	}

	@Override
	public void validate(Object target, Errors errors) {
		delegate.get().validate(target, errors);
	}

	@Override
	public void validate(Object target, Errors errors, Object... validationHints) {
		delegate.get().validate(target, errors, validationHints);
	}

	@Override
	public <T> Set<ConstraintViolation<T>> validate(T object, Class<?>... groups) {
		return delegate.get().validate(object, groups);
	}

	@Override
	public <T> Set<ConstraintViolation<T>> validateProperty(T object, String propertyName, Class<?>... groups) {
		return delegate.get().validateProperty(object, propertyName, groups);
	}

	@Override
	public <T> Set<ConstraintViolation<T>> validateValue(
		Class<T> beanType,
		String propertyName,
		Object value,
		Class<?>... groups
	) {
		return delegate.get().validateValue(beanType, propertyName, value, groups);
	}

	@Override
	public BeanDescriptor getConstraintsForClass(Class<?> clazz) {
		return delegate.get().getConstraintsForClass(clazz);
	}

	@Override
	public <T> T unwrap(Class<T> type) {
		return delegate.get().unwrap(type);
	}

	@Override
	public ExecutableValidator forExecutables() {
		return delegate.get().forExecutables();
	}

	@Override
	public void destroy() {
		delegate.get().destroy();
	}
}
