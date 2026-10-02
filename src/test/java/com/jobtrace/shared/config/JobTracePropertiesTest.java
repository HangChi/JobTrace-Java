package com.jobtrace.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.util.List;
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
    void acceptsACompleteEnabledBridgeConfiguration() {
        var properties = new JobTraceProperties(
                new JobTraceProperties.Database(
                        "jdbc:postgresql://localhost/jobtrace",
                        "jobtrace",
                        "secret"),
                new JobTraceProperties.Migration(false),
                new JobTraceProperties.AuthBridge(
                        true,
                        "legacy-jobtrace",
                        "jobtrace-java",
                        30,
                        5,
                        "jobtrace:auth-bridge:replay",
                        List.of(new JobTraceProperties.SigningKey(
                                "2026-10-primary",
                                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))));

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(properties)).isEmpty();
        }
    }

    @Test
    void rejectsBlankDatabaseSettingsAndInvalidEnabledBridge() {
        var properties = new JobTraceProperties(
                new JobTraceProperties.Database("", "", ""),
                new JobTraceProperties.Migration(false),
                new JobTraceProperties.AuthBridge(
                        true,
                        "",
                        "",
                        31,
                        6,
                        "",
                        List.of(new JobTraceProperties.SigningKey("bad key", "too-short"))));

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(properties)).hasSize(10);
        }
    }

    @Test
    void disabledBridgeAllowsNoKeysAndFiltersIncompleteEntries() {
        var bridge = new JobTraceProperties.AuthBridge(
                false,
                "legacy-jobtrace",
                "jobtrace-java",
                30,
                5,
                "jobtrace:auth-bridge:replay",
                List.of(
                        new JobTraceProperties.SigningKey("", ""),
                        new JobTraceProperties.SigningKey("current", "")));
        var bridgeWithNullList = new JobTraceProperties.AuthBridge(
                false,
                "legacy-jobtrace",
                "jobtrace-java",
                30,
                5,
                "jobtrace:auth-bridge:replay",
                null);

        assertThat(bridge.keys()).isEmpty();
        assertThat(bridge.isKeyConfigurationValid()).isTrue();
        assertThat(bridgeWithNullList.keys()).isEmpty();
    }

    @Test
    void enabledBridgeRejectsDuplicateKeyIdentifiers() {
        var duplicateKeys = List.of(
                new JobTraceProperties.SigningKey(
                        "current",
                        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
                new JobTraceProperties.SigningKey(
                        "current",
                        "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"));
        var bridge = new JobTraceProperties.AuthBridge(
                true,
                "legacy-jobtrace",
                "jobtrace-java",
                30,
                5,
                "jobtrace:auth-bridge:replay",
                duplicateKeys);

        assertThat(bridge.isKeyConfigurationValid()).isFalse();
    }
}
