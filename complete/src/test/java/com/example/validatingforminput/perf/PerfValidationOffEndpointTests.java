package com.example.validatingforminput.perf;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "com.ampp.validation-enabled=false",
    "com.ampp.perf-endpoints.enabled=true"
})
@AutoConfigureMockMvc
class PerfValidationOffEndpointTests extends AbstractPerfValidationEndpointTests {

    @Test
    void shouldAllowInvalidMapPayloadWhenValidationIsOff() throws Exception {
        assertOk("/perf/validate/extensions/map", PerfPayloadFixtures.invalidMapRequestBody("shallow"));
    }

    @Test
    void shouldAllowInvalidRawPayloadWhenValidationIsOff() throws Exception {
        assertOk("/perf/validate/extensions/raw", PerfPayloadFixtures.invalidRawRequestBody("deep"));
    }

    @Test
    void shouldAllowMalformedRawPayloadWhenValidationIsOff() throws Exception {
        assertOk("/perf/validate/extensions/raw", PerfPayloadFixtures.malformedRawRequestBody());
    }
}
