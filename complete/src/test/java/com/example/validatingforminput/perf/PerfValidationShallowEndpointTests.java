package com.example.validatingforminput.perf;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "com.ampp.perf-endpoints.enabled=true",
    "com.ampp.businessValidationOverride[0].fullClassName=com.example.validatingforminput.perf.PerfMapValidationRequest",
    "com.ampp.businessValidationOverride[0].fields[0].fieldName=extensions",
    "com.ampp.businessValidationOverride[0].fields[0].constraints[0].constraintType=Extensions",
    "com.ampp.businessValidationOverride[0].fields[0].constraints[0].params.jsonPath=$.vendorExtensionCode",
    "com.ampp.businessValidationOverride[0].fields[0].constraints[0].params.regexp=^[A-Z]{3}-[0-9]{4}$",
    "com.ampp.businessValidationOverride[1].fullClassName=com.example.validatingforminput.perf.PerfRawValidationRequest",
    "com.ampp.businessValidationOverride[1].fields[0].fieldName=extensions",
    "com.ampp.businessValidationOverride[1].fields[0].constraints[0].constraintType=Extensions",
    "com.ampp.businessValidationOverride[1].fields[0].constraints[0].params.jsonPath=$.vendorExtensionCode",
    "com.ampp.businessValidationOverride[1].fields[0].constraints[0].params.regexp=^[A-Z]{3}-[0-9]{4}$"
})
@AutoConfigureMockMvc
class PerfValidationShallowEndpointTests extends AbstractPerfValidationEndpointTests {

    @Test
    void shouldAcceptShallowMapPayload() throws Exception {
        assertOk("/perf/validate/extensions/map", PerfPayloadFixtures.mapRequestBody("shallow"));
    }

    @Test
    void shouldAcceptDeepMapPayloadWithShallowRule() throws Exception {
        assertOk("/perf/validate/extensions/map", PerfPayloadFixtures.mapRequestBody("deep"));
    }

    @Test
    void shouldRejectInvalidShallowMapPayload() throws Exception {
        assertBadRequest("/perf/validate/extensions/map", PerfPayloadFixtures.invalidMapRequestBody("shallow"));
    }

    @Test
    void shouldAcceptShallowRawPayload() throws Exception {
        assertOk("/perf/validate/extensions/raw", PerfPayloadFixtures.rawRequestBody("shallow"));
    }

    @Test
    void shouldRejectInvalidShallowRawPayload() throws Exception {
        assertBadRequest("/perf/validate/extensions/raw", PerfPayloadFixtures.invalidRawRequestBody("shallow"));
    }

    @Test
    void shouldRejectMalformedRawPayload() throws Exception {
        assertBadRequest("/perf/validate/extensions/raw", PerfPayloadFixtures.malformedRawRequestBody());
    }
}
