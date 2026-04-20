package com.example.validatingforminput.perf;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class PerfPayloadFixtures {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final String VALID_CART_ID = "CART-7F9A21";
    private static final String VALID_CUSTOMER_ID = "CUSTOMER-1001";
    private static final String VALID_CURRENCY = "USD";
    private static final BigDecimal VALID_TOTAL_AMOUNT = new BigDecimal("1499.99");

    private static final ShoppingCartPayload SHALLOW_CART = new ShoppingCartPayload("ABC-1234", null);
    private static final ShoppingCartPayload INVALID_SHALLOW_CART = new ShoppingCartPayload("abc-1234", null);

    private static final ShoppingCartPayload DEEP_CART = new ShoppingCartPayload(
        "ABC-1234",
        List.of(
            itemWithCode("Fiber 500", "ABC-1234"),
            itemWithCode("Fiber 800", "DEF-5678"),
            itemWithCode("Fiber 1000", "GHI-9012")));

    private static final ShoppingCartPayload INVALID_DEEP_CART = new ShoppingCartPayload(
        "ABC-1234",
        List.of(
            itemWithCode("Fiber 500", "ABC-1234"),
            itemWithCode("Fiber 800", "DEF-5678"),
            itemWithCode("Fiber 1000", "ghi-9012")));

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
        return rawRequest(shallowExtensionsJson());
    }

    public static PerfRawValidationRequest deepRawRequest() {
        return rawRequest(deepExtensionsJson());
    }

    public static PerfRawValidationRequest invalidShallowRawRequest() {
        return rawRequest(invalidShallowExtensionsJson());
    }

    public static PerfRawValidationRequest invalidDeepRawRequest() {
        return rawRequest(invalidDeepExtensionsJson());
    }

    public static PerfRawValidationRequest malformedRawRequest() {
        return rawRequest(MALFORMED_EXTENSIONS_JSON);
    }

    public static String mapRequestBody(String shape) {
        return toJson("deep".equalsIgnoreCase(shape) ? deepMapRequest() : shallowMapRequest());
    }

    public static String rawRequestBody(String shape) {
        return toJson("deep".equalsIgnoreCase(shape) ? deepRawRequest() : shallowRawRequest());
    }

    public static String invalidMapRequestBody(String shape) {
        return toJson("deep".equalsIgnoreCase(shape) ? invalidDeepMapRequest() : invalidShallowMapRequest());
    }

    public static String invalidRawRequestBody(String shape) {
        return toJson("deep".equalsIgnoreCase(shape) ? invalidDeepRawRequest() : invalidShallowRawRequest());
    }

    public static String malformedRawRequestBody() {
        return toJson(malformedRawRequest());
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
        request.setCartId(VALID_CART_ID);
        request.setCustomerId(VALID_CUSTOMER_ID);
        request.setCurrency(VALID_CURRENCY);
        request.setTotalAmount(VALID_TOTAL_AMOUNT);
    }

    private static Map<String, Object> shallowExtensions() {
        return toMap(SHALLOW_CART);
    }

    private static Map<String, Object> invalidShallowExtensions() {
        return toMap(INVALID_SHALLOW_CART);
    }

    private static Map<String, Object> deepExtensions() {
        return toMap(DEEP_CART);
    }

    private static Map<String, Object> invalidDeepExtensions() {
        return toMap(INVALID_DEEP_CART);
    }

    private static String shallowExtensionsJson() {
        return toJson(SHALLOW_CART);
    }

    private static String invalidShallowExtensionsJson() {
        return toJson(INVALID_SHALLOW_CART);
    }

    private static String deepExtensionsJson() {
        return toJson(DEEP_CART);
    }

    private static String invalidDeepExtensionsJson() {
        return toJson(INVALID_DEEP_CART);
    }

    private static ShoppingCartItemPayload itemWithCode(String name, String catalogCode) {
        return new ShoppingCartItemPayload(new ProductOfferingPayload(
            name,
            Map.of(
                "catalogCode", catalogCode,
                "channel", "digital")));
    }

    private static Map<String, Object> toMap(ShoppingCartPayload cart) {
        return OBJECT_MAPPER.convertValue(cart, MAP_TYPE);
    }

    private static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize perf payload", exception);
        }
    }
}
