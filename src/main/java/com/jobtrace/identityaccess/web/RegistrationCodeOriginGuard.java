package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.domain.RegistrationCodeOriginException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Preserves the legacy cross-site and Origin checks for anonymous issuance. */
@Component
public final class RegistrationCodeOriginGuard {

    private final String allowedOrigin;

    public RegistrationCodeOriginGuard(
            @Value("${jobtrace.registration-code.allowed-origin:}") String authUrl) {
        this.allowedOrigin = originOf(authUrl);
    }

    public void assertAllowed(HttpServletRequest request) {
        if ("cross-site".equals(request.getHeader("sec-fetch-site"))) {
            throw new RegistrationCodeOriginException();
        }
        String origin = request.getHeader("origin");
        if (origin != null && !origin.equals(allowedOrigin)) {
            throw new RegistrationCodeOriginException();
        }
    }

    private static String originOf(String authUrl) {
        if (authUrl == null || authUrl.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(authUrl);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null) {
                return "";
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (("http".equals(scheme) && port == 80)
                    || ("https".equals(scheme) && port == 443)) {
                port = -1;
            }
            return new URI(scheme, null, uri.getHost().toLowerCase(Locale.ROOT),
                    port, null, null, null).toString();
        } catch (IllegalArgumentException | URISyntaxException exception) {
            return "";
        }
    }
}
