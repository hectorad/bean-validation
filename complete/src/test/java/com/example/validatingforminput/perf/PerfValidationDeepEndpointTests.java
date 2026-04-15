package com.example.validatingforminput.perf;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "com.ampp.perf-endpoints.enabled=true",
    "com.ampp.businessValidationOverride[0].fullClassName=com.example.validatingforminput.perf.PerfMapValidationRequest",
    "com.ampp.businessValidationOverride[0].fields[0].fieldName=extensions",
    "com.ampp.businessValidationOverride[0].fields[0].constraints[0].constraintType=Extensions",
    "com.ampp.businessValidationOverride[0].fields[0].constraints[0].params.jsonPath=$.vendor.contact.codes[*].value",
    "com.ampp.businessValidationOverride[0].fields[0].constraints[0].params.regexp=^[A-Z]{3}-[0-9]{4}$",
    "com.ampp.businessValidationOverride[1].fullClassName=com.example.validatingforminput.perf.PerfRawValidationRequest",
    "com.ampp.businessValidationOverride[1].fields[0].fieldName=extensions",
    "com.ampp.businessValidationOverride[1].fields[0].constraints[0].constraintType=Extensions",
    "com.ampp.businessValidationOverride[1].fields[0].constraints[0].params.jsonPath=$.vendor.contact.codes[*].value",
    "com.ampp.businessValidationOverride[1].fields[0].constraints[0].params.regexp=^[A-Z]{3}-[0-9]{4}$"
})
@AutoConfigureMockMvc
class PerfValidationDeepEndpointTests extends AbstractPerfValidationEndpointTests {

    @Test
    void shouldAcceptDeepMapPayload() throws Exception {
        assertOk("/perf/validate/extensions/map", PerfPayloadFixtures.mapRequestBody("deep"));
    }

    @Test
    void shouldRejectInvalidDeepMapPayload() throws Exception {
        assertBadRequest("/perf/validate/extensions/map", PerfPayloadFixtures.invalidMapRequestBody("deep"));
    }

    @Test
    void shouldAcceptDeepRawPayload() throws Exception {
        assertOk("/perf/validate/extensions/raw", PerfPayloadFixtures.rawRequestBody("deep"));
    }

    @Test
    void shouldRejectInvalidDeepRawPayload() throws Exception {
        assertBadRequest("/perf/validate/extensions/raw", PerfPayloadFixtures.invalidRawRequestBody("deep"));
    }
}
