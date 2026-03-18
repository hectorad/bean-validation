package com.example.validatingforminput.validation;

import java.util.Enumeration;
import java.util.Objects;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

public class RequestHeaderValidationBypassStrategy implements ValidationBypassStrategy {

    private final String headerName;

    private final String headerValue;

    public RequestHeaderValidationBypassStrategy(String headerName, String headerValue) {
        this.headerName = Objects.requireNonNull(headerName, "headerName must not be null");
        this.headerValue = Objects.requireNonNull(headerValue, "headerValue must not be null");
    }

    @Override
    public boolean shouldBypass() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return false;
        }

        HttpServletRequest request = servletRequestAttributes.getRequest();
        if (request == null) {
            return false;
        }

        Enumeration<String> headerValues = request.getHeaders(headerName);
        while (headerValues != null && headerValues.hasMoreElements()) {
            if (this.headerValue.equals(headerValues.nextElement())) {
                return true;
            }
        }

        return false;
    }
}
