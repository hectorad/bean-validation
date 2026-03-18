package io.github.hectorad.validation.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

import io.github.hectorad.validation.BeanValidationExternalPayloadValidator;
import io.github.hectorad.validation.ExternalPayloadValidator;
import io.github.hectorad.validation.ValidationResult;
import io.github.hectorad.validation.autoconfigure.ValidationAutoConfiguration;
import io.github.hectorad.validation.feign.autoconfigure.FeignValidationAutoConfiguration;
import feign.Capability;
import feign.Request;
import feign.Response;
import feign.codec.Decoder;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class FeignValidationAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            ValidationAutoConfiguration.class,
            FeignValidationAutoConfiguration.class));

    @Test
    void shouldNotCreateFeignCapabilityWhenFeatureFlagIsDisabled() {
        contextRunner.run(context -> assertThat(context.getBeansOfType(Capability.class)).isEmpty());
    }

    @Test
    void shouldCreateFeignCapabilityWhenFeatureFlagIsEnabled() {
        contextRunner
            .withPropertyValues("hector.validation.feign.enabled=true")
            .run(context -> assertThat(context.getBeansOfType(Capability.class))
                .containsValue(context.getBean(ValidatingFeignCapability.class)));
    }

    @Test
    void shouldReturnDecodedObjectWhenFeignPayloadIsValid() throws Exception {
        LocalValidatorFactoryBean validatorFactoryBean = validatorFactoryBean();
        ExternalPayloadValidator externalPayloadValidator = new BeanValidationExternalPayloadValidator(validatorFactoryBean);
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
        ExternalPayloadValidator externalPayloadValidator = new BeanValidationExternalPayloadValidator(validatorFactoryBean);
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

    private LocalValidatorFactoryBean validatorFactoryBean() {
        LocalValidatorFactoryBean validatorFactoryBean = new LocalValidatorFactoryBean();
        validatorFactoryBean.setMessageInterpolator(new ParameterMessageInterpolator());
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
