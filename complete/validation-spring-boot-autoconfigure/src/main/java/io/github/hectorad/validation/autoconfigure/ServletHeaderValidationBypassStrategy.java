package io.github.hectorad.validation.autoconfigure;

import java.util.Enumeration;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

public class ServletHeaderValidationBypassStrategy implements ValidationBypassStrategy {

    private final ValidationProperties.HttpBypass httpBypass;

    public ServletHeaderValidationBypassStrategy(ValidationProperties.HttpBypass httpBypass) {
        this.httpBypass = httpBypass;
    }

    @Override
    public boolean shouldBypassValidation() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return false;
        }

        HttpServletRequest request = servletRequestAttributes.getRequest();
        if (request == null) {
            return false;
        }

        Enumeration<String> headerValues = request.getHeaders(httpBypass.getHeaderName());
        while (headerValues != null && headerValues.hasMoreElements()) {
            if (httpBypass.getHeaderValue().equals(headerValues.nextElement())) {
                return true;
            }
        }

        return false;
    }
}
