package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.AssertionVerifier;
import com.jobtrace.identityaccess.application.ClaimAssertionUseCase;
import com.jobtrace.identityaccess.domain.ReplayGuard;
import com.jobtrace.identityaccess.web.BridgeAuthenticationFilter;
import com.jobtrace.shared.security.BridgeAuthenticationEntryPoint;
import com.jobtrace.shared.security.SecurityChainCustomizer;
import com.jobtrace.shared.config.JobTraceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/** Application wiring kept separate from the identity-access domain. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "jobtrace.auth-bridge.enabled", havingValue = "true")
public class BridgeIdentityAccessConfiguration {

    @Bean
    ClaimAssertionUseCase claimAssertionUseCase(
            AssertionVerifier verifier,
            ReplayGuard replayGuard,
            JobTraceProperties properties) {
        return new ClaimAssertionUseCase(verifier, replayGuard, properties.authBridge());
    }

    @Bean
    SecurityChainCustomizer bridgeSecurityChainCustomizer(
            BridgeAuthenticationFilter filter,
            BridgeAuthenticationEntryPoint entryPoint) {
        return http -> {
            http.addFilterBefore(filter, AnonymousAuthenticationFilter.class);
            http.exceptionHandling(configurer ->
                    configurer.authenticationEntryPoint(entryPoint));
        };
    }
}
