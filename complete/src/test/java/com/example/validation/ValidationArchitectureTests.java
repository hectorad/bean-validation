package com.example.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ValidationArchitectureTests {

	@Test
	void shouldVerifyApplicationModules() {
		ApplicationModules.of(ValidationArchitectureRoot.class).verify();
	}

	@Test
	void shouldRestrictInternalRuntimePackagesToAutoconfigureModule() {
		ApplicationModules modules = ApplicationModules.of(ValidationArchitectureRoot.class);

		modules.stream().forEach(module -> module.getDirectDependencies(modules).stream().forEach(dependency -> {
			String targetPackage = dependency.getTargetType().getPackageName();
			if (targetPackage.endsWith(".internal") || targetPackage.contains(".internal.")) {
				assertThat(module.getName()).isEqualTo("autoconfigure");
			}
		}));
	}
}
