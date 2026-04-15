package com.example.validatingforminput.perf;

import java.util.Map;

public class PerfMapValidationRequest extends AbstractPerfValidationRequest {

    private Map<String, Object> extensions;

    public Map<String, Object> getExtensions() {
        return extensions;
    }

    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions;
    }
}
