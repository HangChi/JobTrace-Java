package com.jobtrace.shared.security;

import com.jobtrace.shared.web.SpaForwardController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
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
                        .anyRequest()
                        .authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
