package com.example.validation.core.internal;

import com.example.validation.core.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.example.validation.autoconfigure.FeignValidationAutoConfiguration;
import com.example.validation.autoconfigure.ValidationAutoConfiguration;
import com.example.validation.feign.internal.DefaultFeignValidationCapabilityFactory;
import com.example.validation.feign.spi.FeignValidationCapabilityFactory;
import com.example.validation.feign.api.FeignResponseValidationException;
import com.example.validation.feign.internal.ValidatingFeignCapability;

import feign.Capability;
import feign.Feign;
import feign.Request;
import feign.Response;
import feign.codec.Decoder;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class FeignValidationAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            ValidationAutoConfiguration.class,
            FeignValidationAutoConfiguration.class));

    @Test
    void shouldNotCreateFeignCapabilityWhenFeatureFlagIsDisabled() {
        contextRunner.run(context -> {
            assertThat(context.getBeansOfType(FeignValidationCapabilityFactory.class)).isEmpty();
            assertThat(context.getBeansOfType(Capability.class)).isEmpty();
        });
    }

    @Test
    void shouldCreateFeignValidationCapabilityFactoryWhenFeatureFlagIsEnabled() {
        contextRunner
            .withPropertyValues("com.ampp.feign-response-validation.enabled=true")
            .run(context -> {
                assertThat(context.getBeansOfType(FeignValidationCapabilityFactory.class)).hasSize(1);
                assertThat(context.getBeansOfType(Capability.class)).isEmpty();
                assertThat(context.getBeanProvider(Capability.class).orderedStream().toList()).isEmpty();
            });
    }

    @Test
    void shouldReturnDecodedObjectWhenFeignPayloadIsValid() throws Exception {
        LocalValidatorFactoryBean validatorFactoryBean = validatorFactoryBean();
        BeanValidationExternalPayloadValidator externalPayloadValidator = new BeanValidationExternalPayloadValidator(validatorFactoryBean);
        ValidatingFeignCapability capability = new ValidatingFeignCapability(externalPayloadValidator);
        FeignPayload payload = new FeignPayload();
        payload.setName("valid");

        Decoder decoder = capability.enrich((Decoder) (response, type) -> payload);

        assertThat(decoder.decode(response(), FeignPayload.class)).isSameAs(payload);
        validatorFactoryBean.destroy();
    }

    @Test
    void shouldThrowValidationExceptionWithSameViolationsAsManualValidation() throws Exception {
        LocalValidatorFactoryBean validatorFactoryBean = validatorFactoryBean();
        BeanValidationExternalPayloadValidator externalPayloadValidator = new BeanValidationExternalPayloadValidator(validatorFactoryBean);
        ValidatingFeignCapability capability = new ValidatingFeignCapability(externalPayloadValidator);
        FeignPayload payload = new FeignPayload();
        payload.setName("");
        ValidationResult<FeignPayload> manualResult = externalPayloadValidator.validate(payload);
        Decoder decoder = capability.enrich((Decoder) (response, type) -> payload);

        assertThatThrownBy(() -> decoder.decode(response(), FeignPayload.class))
            .isInstanceOfSatisfying(FeignResponseValidationException.class, exception -> {
                assertThat(exception.getDecodedPayload()).isSameAs(payload);
                assertThat(exception.getValidationResult().violations()).isEqualTo(manualResult.violations());
            });

        validatorFactoryBean.destroy();
    }

    @Test
    void shouldCreateCapabilityForManualFeignBuilderUsage() {
        LocalValidatorFactoryBean validatorFactoryBean = validatorFactoryBean();
        BeanValidationExternalPayloadValidator externalPayloadValidator = new BeanValidationExternalPayloadValidator(validatorFactoryBean);
        FeignValidationCapabilityFactory factory = new DefaultFeignValidationCapabilityFactory(externalPayloadValidator);

        Capability capability = factory.create();

        assertThat(capability).isInstanceOf(ValidatingFeignCapability.class);
        assertThatCode(() -> Feign.builder().addCapability(capability)).doesNotThrowAnyException();
        validatorFactoryBean.destroy();
    }

    private LocalValidatorFactoryBean validatorFactoryBean() {
        LocalValidatorFactoryBean validatorFactoryBean = new LocalValidatorFactoryBean();
        validatorFactoryBean.afterPropertiesSet();
        return validatorFactoryBean;
    }

    private Response response() {
        Request request = Request.create(
            Request.HttpMethod.GET,
            "http://localhost/payload",
            Map.of(),
            null,
            StandardCharsets.UTF_8,
            null);
        return Response.builder()
            .status(200)
            .reason("OK")
            .request(request)
            .headers(Map.<String, Collection<String>>of())
            .build();
    }

    static class FeignPayload {

        @NotBlank
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
