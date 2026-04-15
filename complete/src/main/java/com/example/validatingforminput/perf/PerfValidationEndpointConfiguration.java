package com.example.validatingforminput.perf;

import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/perf/validate/extensions")
@ConditionalOnProperty(prefix = "com.ampp.perf-endpoints", name = "enabled", havingValue = "true")
public class PerfValidationEndpointConfiguration {

    private final Validator validator;

    public PerfValidationEndpointConfiguration(Validator validator) {
        this.validator = validator;
    }

    @PostMapping("/map")
    ResponseEntity<Map<String, Object>> validateMap(@RequestBody PerfMapValidationRequest request) {
        return validate(request);
    }

    @PostMapping("/raw")
    ResponseEntity<Map<String, Object>> validateRaw(@RequestBody PerfRawValidationRequest request) {
        return validate(request);
    }

    private ResponseEntity<Map<String, Object>> validate(Object request) {
        Set<? extends ConstraintViolation<?>> violations = validator.validate(request);
        if (violations.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "ok"));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of(
                "status", "field-errors",
                "violations", violations.size()));
    }
}
