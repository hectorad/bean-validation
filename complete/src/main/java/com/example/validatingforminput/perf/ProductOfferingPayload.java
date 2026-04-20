package com.example.validatingforminput.perf;

import java.util.Map;

public record ProductOfferingPayload(
    String name,
    Map<String, String> tags
) {
}
