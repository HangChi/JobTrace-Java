package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.domain.LoginUnavailableException;
import com.jobtrace.identityaccess.web.BrowserAuthenticationFilter;
import com.jobtrace.shared.security.SecurityChainCustomizer;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Installs browser authentication inside the security chain, not as a servlet filter. */
@Configuration(proxyBeanMethods = false)
public class BrowserIdentityAccessConfiguration {
    private static final Set<String> ACCOUNT_WRITES = Set.of(
            "PATCH /api/profile",
            "POST /api/profile/password",
            "POST /api/profile/avatar",
            "DELETE /api/profile/email",
            "PATCH /api/profile/email",
            "POST /api/profile/email/code",
            "DELETE /api/profile/sessions");
    private static final Pattern ADMIN_USER_DETAIL = Pattern.compile(
            "/api/admin/users/[^/?#]{1,128}");

    @Bean
    FilterRegistrationBean<BrowserAuthenticationFilter> browserFilterRegistration(
            BrowserAuthenticationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityChainCustomizer browserSecurityChainCustomizer(BrowserAuthenticationFilter filter,
            BetterAuthSessionCookie cookie) {
        return http -> {
            http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
            http.csrf(csrf -> csrf.ignoringRequestMatchers(request -> {
                String method = request.getMethod();
                String path = request.getRequestURI();
                String header = request.getHeader("Cookie");
                if (header == null || !browserWrite(method, path)) {
                    return false;
                }
                try {
                    return cookie.verifiedToken(header) != null;
                } catch (LoginUnavailableException exception) {
                    return false;
                }
            }));
        };
    }

    static boolean browserWrite(String method, String path) {
        return ACCOUNT_WRITES.contains(method + " " + path)
                || ("PATCH".equals(method) && ADMIN_USER_DETAIL.matcher(path).matches());
    }
}
