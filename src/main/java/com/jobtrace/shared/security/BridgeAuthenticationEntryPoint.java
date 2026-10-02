package com.jobtrace.shared.security;

import com.jobtrace.shared.observability.SafeLogger;
import com.jobtrace.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Produces one non-disclosing response shape for all bridge authentication failures. */
@Component
@ConditionalOnProperty(name = "jobtrace.auth-bridge.enabled", havingValue = "true")
public final class BridgeAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(BridgeAuthenticationEntryPoint.class);

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        commence(request, response, "ABSENT");
    }

    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            String failure) throws IOException {
        String requestId = requestId(request);
        SafeLogger.info(
                LOGGER,
                "bridge_authentication_rejected",
                Map.of("failure", failure, "requestId", requestId));
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"urn:jobtrace:problem:unauthorized","title":"Unauthorized",\
                "status":401,"detail":"Authentication is required.",\
                "code":"unauthorized","requestId":"%s"}
                """.formatted(requestId).replace("\n", ""));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return value instanceof String requestId ? requestId : "unavailable";
    }
}
