package io.github.hectorad.validation.sample;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Import;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@TestConfiguration(proxyBeanMethods = false)
@Import(RequestValidationBypassTestConfiguration.RequestValidationProbeController.class)
public class RequestValidationBypassTestConfiguration {

    @RestController
    static class RequestValidationProbeController {

        private final PersonValidationService personValidationService;

        RequestValidationProbeController(PersonValidationService personValidationService) {
            this.personValidationService = personValidationService;
        }

        @PostMapping("/validation-probe/mvc")
        ResponseEntity<String> validateMvc(@Valid PersonForm personForm, BindingResult bindingResult) {
            return bindingResult.hasFieldErrors()
                ? ResponseEntity.badRequest().body("field-errors")
                : ResponseEntity.ok("ok");
        }

        @PostMapping("/validation-probe/method")
        ResponseEntity<String> validateMethod() {
            PersonForm form = new PersonForm();
            form.setName("");
            form.setAge(5);
            personValidationService.validate(form);
            return ResponseEntity.ok("ok");
        }
    }
}
