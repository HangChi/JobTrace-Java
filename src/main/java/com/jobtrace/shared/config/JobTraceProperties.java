package com.jobtrace.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    public record AuthBridge(@Size(min = 32) String secret) {
    }
}

