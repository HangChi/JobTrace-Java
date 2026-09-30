package com.jobtrace.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class JobTracePropertiesTest {

    @Test
    void acceptsACompleteConfigurationWithoutAnAuthBridge() {
        var properties = new JobTraceProperties(
                new JobTraceProperties.Database(
                        "jdbc:postgresql://localhost/jobtrace",
                        "jobtrace",
                        "secret"),
                new JobTraceProperties.Migration(false),
                null);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(properties)).isEmpty();
        }
    }

    @Test
    void rejectsBlankDatabaseSettingsAndAShortBridgeSecret() {
        var properties = new JobTraceProperties(
                new JobTraceProperties.Database("", "", ""),
                new JobTraceProperties.Migration(false),
                new JobTraceProperties.AuthBridge("too-short"));

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(properties)).hasSize(4);
        }
    }
}

