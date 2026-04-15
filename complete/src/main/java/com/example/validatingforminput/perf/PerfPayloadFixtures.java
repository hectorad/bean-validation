package com.example.validatingforminput.perf;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class PerfPayloadFixtures {

    private static final String VALID_NAME = "Robert";
    private static final Integer VALID_AGE = 30;
    private static final BigDecimal VALID_SALARY = new BigDecimal("2000.00");

    private static final String SHALLOW_EXTENSIONS_JSON = "{\"vendorExtensionCode\":\"ABC-1234\"}";
    private static final String INVALID_SHALLOW_EXTENSIONS_JSON = "{\"vendorExtensionCode\":\"abc-1234\"}";

    private static final String DEEP_EXTENSIONS_JSON = """
        {"vendorExtensionCode":"ABC-1234","vendor":{"contact":{"codes":[{"value":"ABC-1234"},{"value":"DEF-5678"},{"value":"GHI-9012"}]}}}
        """.trim();

    private static final String INVALID_DEEP_EXTENSIONS_JSON = """
        {"vendorExtensionCode":"ABC-1234","vendor":{"contact":{"codes":[{"value":"ABC-1234"},{"value":"DEF-5678"},{"value":"ghi-9012"}]}}}
        """.trim();

    private static final String MALFORMED_EXTENSIONS_JSON = "{ not-json";

    private PerfPayloadFixtures() {
    }

    public static PerfMapValidationRequest shallowMapRequest() {
        return mapRequest(shallowExtensions());
    }

    public static PerfMapValidationRequest deepMapRequest() {
        return mapRequest(deepExtensions());
    }

    public static PerfMapValidationRequest invalidShallowMapRequest() {
        return mapRequest(invalidShallowExtensions());
    }

    public static PerfMapValidationRequest invalidDeepMapRequest() {
        return mapRequest(invalidDeepExtensions());
    }

    public static PerfRawValidationRequest shallowRawRequest() {
        return rawRequest(SHALLOW_EXTENSIONS_JSON);
    }

    public static PerfRawValidationRequest deepRawRequest() {
        return rawRequest(DEEP_EXTENSIONS_JSON);
    }

    public static PerfRawValidationRequest invalidShallowRawRequest() {
        return rawRequest(INVALID_SHALLOW_EXTENSIONS_JSON);
    }

    public static PerfRawValidationRequest invalidDeepRawRequest() {
        return rawRequest(INVALID_DEEP_EXTENSIONS_JSON);
    }

    public static PerfRawValidationRequest malformedRawRequest() {
        return rawRequest(MALFORMED_EXTENSIONS_JSON);
    }

    public static String mapRequestBody(String shape) {
        String extensionsJson = "deep".equalsIgnoreCase(shape) ? DEEP_EXTENSIONS_JSON : SHALLOW_EXTENSIONS_JSON;
        return """
            {"name":"%s","age":%d,"salary":%s,"extensions":%s}
            """.formatted(VALID_NAME, VALID_AGE, VALID_SALARY.toPlainString(), extensionsJson).trim();
    }

    public static String rawRequestBody(String shape) {
        String extensionsJson = "deep".equalsIgnoreCase(shape) ? DEEP_EXTENSIONS_JSON : SHALLOW_EXTENSIONS_JSON;
        return """
            {"name":"%s","age":%d,"salary":%s,"extensions":"%s"}
            """.formatted(
                VALID_NAME,
                VALID_AGE,
                VALID_SALARY.toPlainString(),
                escapeJson(extensionsJson))
            .trim();
    }

    public static String invalidMapRequestBody(String shape) {
        String extensionsJson = "deep".equalsIgnoreCase(shape) ? INVALID_DEEP_EXTENSIONS_JSON : INVALID_SHALLOW_EXTENSIONS_JSON;
        return """
            {"name":"%s","age":%d,"salary":%s,"extensions":%s}
            """.formatted(VALID_NAME, VALID_AGE, VALID_SALARY.toPlainString(), extensionsJson).trim();
    }

    public static String invalidRawRequestBody(String shape) {
        String extensionsJson = "deep".equalsIgnoreCase(shape) ? INVALID_DEEP_EXTENSIONS_JSON : INVALID_SHALLOW_EXTENSIONS_JSON;
        return """
            {"name":"%s","age":%d,"salary":%s,"extensions":"%s"}
            """.formatted(
                VALID_NAME,
                VALID_AGE,
                VALID_SALARY.toPlainString(),
                escapeJson(extensionsJson))
            .trim();
    }

    public static String malformedRawRequestBody() {
        return """
            {"name":"%s","age":%d,"salary":%s,"extensions":"%s"}
            """.formatted(
                VALID_NAME,
                VALID_AGE,
                VALID_SALARY.toPlainString(),
                escapeJson(MALFORMED_EXTENSIONS_JSON))
            .trim();
    }

    private static PerfMapValidationRequest mapRequest(Map<String, Object> extensions) {
        PerfMapValidationRequest request = new PerfMapValidationRequest();
        populateBaseFields(request);
        request.setExtensions(extensions);
        return request;
    }

    private static PerfRawValidationRequest rawRequest(String extensions) {
        PerfRawValidationRequest request = new PerfRawValidationRequest();
        populateBaseFields(request);
        request.setExtensions(extensions);
        return request;
    }

    private static void populateBaseFields(AbstractPerfValidationRequest request) {
        request.setName(VALID_NAME);
        request.setAge(VALID_AGE);
        request.setSalary(VALID_SALARY);
    }

    private static Map<String, Object> shallowExtensions() {
        return Map.of("vendorExtensionCode", "ABC-1234");
    }

    private static Map<String, Object> invalidShallowExtensions() {
        return Map.of("vendorExtensionCode", "abc-1234");
    }

    private static Map<String, Object> deepExtensions() {
        return Map.of(
            "vendorExtensionCode", "ABC-1234",
            "vendor", Map.of(
                "contact", Map.of(
                    "codes", List.of(
                        Map.of("value", "ABC-1234"),
                        Map.of("value", "DEF-5678"),
                        Map.of("value", "GHI-9012")))));
    }

    private static Map<String, Object> invalidDeepExtensions() {
        return Map.of(
            "vendorExtensionCode", "ABC-1234",
            "vendor", Map.of(
                "contact", Map.of(
                    "codes", List.of(
                        Map.of("value", "ABC-1234"),
                        Map.of("value", "DEF-5678"),
                        Map.of("value", "ghi-9012")))));
    }

    private static String escapeJson(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
