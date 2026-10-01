package com.jobtrace.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jobtrace")
public record JobTraceProperties(
        @Valid Database database,
        @Valid Migration migration,
        @Valid AuthBridge authBridge) {

    public record Database(
            @NotBlank String url,
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record Migration(boolean flywayEnabled) {
    }

    public record AuthBridge(
            boolean enabled,
            @NotBlank String issuer,
            @NotBlank String audience,
            @Min(1) @Max(30) int maxLifetimeSeconds,
            @Min(0) @Max(5) int clockSkewSeconds,
            @NotBlank String replayKeyPrefix,
            List<@Valid SigningKey> keys) {

        public AuthBridge {
            keys = keys == null ? List.of() : keys.stream()
                    .filter(Objects::nonNull)
                    .filter(SigningKey::isConfigured)
                    .toList();
        }

        @AssertTrue(message = "enabled auth bridge requires at least one signing key")
        public boolean isKeyConfigurationValid() {
            return !enabled || (!keys.isEmpty()
                    && keys.stream().map(SigningKey::id).distinct().count() == keys.size());
        }
    }

    public record SigningKey(
            @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String id,
            @Size(min = 43, max = 256) String secret) {

        boolean isConfigured() {
            return id != null && !id.isBlank() && secret != null && !secret.isBlank();
        }
    }
}
