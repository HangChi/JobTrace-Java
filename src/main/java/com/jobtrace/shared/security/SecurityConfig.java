package com.jobtrace.shared.security;

import com.jobtrace.shared.web.SpaForwardController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<SecurityChainCustomizer> customizers) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/assets/**",
                                "/*.svg",
                                "/api/health/**",
                                "/actuator/health/**",
                                SpaForwardController.BROWSER_ROUTE,
                                SpaForwardController.NESTED_BROWSER_ROUTE)
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/email-code")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/session")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/request-password-reset")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/reset-password/*")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/reset-password")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(request ->
                        "POST".equals(request.getMethod())
                        && ("/api/auth/email-code".equals(request.getRequestURI())
                        || "/api/auth/register".equals(request.getRequestURI())
                        || "/api/auth/login".equals(request.getRequestURI())
                        || "/api/auth/logout".equals(request.getRequestURI())
                        || "/api/auth/request-password-reset".equals(request.getRequestURI())
                        || "/api/auth/reset-password".equals(request.getRequestURI()))))
                .httpBasic(Customizer.withDefaults());
        for (SecurityChainCustomizer customizer : customizers) {
            customizer.customize(http);
        }
        return http.build();
    }
}
