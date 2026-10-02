package com.jobtrace.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MigrationSliceTest {

    @Test
    void activationRequiresEvidenceRollbackAndOneLegacyWriteOwner() {
        MigrationSlice implemented = new MigrationSlice(
                "analytics-summary",
                MigrationSlice.Module.ANALYTICS,
                Set.of("GET /api/analytics/summary"),
                "legacy-jobtrace",
                "jobtrace-java",
                "legacy-jobtrace",
                MigrationSlice.State.IMPLEMENTED,
                "route analytics-summary to legacy-jobtrace",
                List.of());

        assertThatThrownBy(() -> implemented.transitionTo(MigrationSlice.State.VERIFIED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("evidence");

        MigrationSlice verified = implemented.withEvidence("contract fixtures pass")
                .withEvidence(MigrationSlice.BRIDGE_EVIDENCE)
                .withEvidence(MigrationSlice.ROLLBACK_EVIDENCE)
                .transitionTo(MigrationSlice.State.VERIFIED);
        assertThat(verified.transitionTo(MigrationSlice.State.ACTIVE).state())
                .isEqualTo(MigrationSlice.State.ACTIVE);
    }

    @Test
    void invalidSlugAndUnsupportedTransitionAreRejected() {
        assertThatThrownBy(() -> new MigrationSlice(
                "Analytics Summary",
                MigrationSlice.Module.ANALYTICS,
                Set.of("summary"),
                "legacy",
                "java",
                "legacy",
                MigrationSlice.State.PLANNED,
                "rollback",
                List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        MigrationSlice planned = MigrationSlice.planned(
                "analytics-summary",
                MigrationSlice.Module.ANALYTICS,
                Set.of("GET /api/analytics/summary"),
                "legacy-jobtrace",
                "jobtrace-java",
                "legacy-jobtrace");
        assertThatThrownBy(() -> planned.transitionTo(MigrationSlice.State.ACTIVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLANNED");
    }

    @Test
    void supportsTheCompleteLifecycleAndRollbackRecovery() {
        MigrationSlice implemented = MigrationSlice.planned(
                        "analytics-summary",
                        MigrationSlice.Module.ANALYTICS,
                        Set.of("GET /api/analytics/summary"),
                        "legacy-jobtrace",
                        "jobtrace-java",
                        "legacy-jobtrace")
                .transitionTo(MigrationSlice.State.IMPLEMENTED)
                .withEvidence("contract fixtures pass")
                .withEvidence(MigrationSlice.BRIDGE_EVIDENCE)
                .withEvidence(MigrationSlice.ROLLBACK_EVIDENCE)
                .withEvidence(MigrationSlice.OBSERVATION_EVIDENCE);

        MigrationSlice verified = implemented.transitionTo(MigrationSlice.State.VERIFIED);
        MigrationSlice active = new MigrationSlice(
                verified.id(),
                verified.module(),
                verified.capabilities(),
                verified.legacyOwner(),
                verified.targetOwner(),
                verified.writeOwner(),
                verified.state(),
                "route analytics-summary to legacy-jobtrace",
                verified.evidence());
        MigrationSlice retired = active.transitionTo(MigrationSlice.State.ACTIVE)
                .transitionTo(MigrationSlice.State.OBSERVED)
                .transitionTo(MigrationSlice.State.RETIRED);

        assertThatThrownBy(() -> retired.transitionTo(MigrationSlice.State.ACTIVE))
                .isInstanceOf(IllegalStateException.class);

        MigrationSlice recovered = active.transitionTo(MigrationSlice.State.ACTIVE)
                .transitionTo(MigrationSlice.State.ROLLED_BACK)
                .transitionTo(MigrationSlice.State.IMPLEMENTED);
        assertThat(recovered.state()).isEqualTo(MigrationSlice.State.IMPLEMENTED);
    }

    @Test
    void rejectsMissingActivationMetadataAndBlankValues() {
        assertThatThrownBy(() -> new MigrationSlice(
                "analytics-summary",
                MigrationSlice.Module.ANALYTICS,
                Set.of("summary"),
                "legacy",
                "java",
                "legacy",
                MigrationSlice.State.ACTIVE,
                null,
                List.of("verified")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rollback");

        assertThatThrownBy(() -> new MigrationSlice(
                "analytics-summary",
                MigrationSlice.Module.ANALYTICS,
                Set.of(" "),
                "legacy",
                "java",
                "legacy",
                MigrationSlice.State.PLANNED,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capabilities");
    }

    @Test
    void activeAndObservedStatesRequireBridgeRollbackAndObservationEvidence() {
        MigrationSlice verified = MigrationSlice.planned(
                        "analytics-summary",
                        MigrationSlice.Module.ANALYTICS,
                        Set.of("GET /api/analytics/summary"),
                        "legacy-jobtrace",
                        "jobtrace-java",
                        "legacy-jobtrace")
                .transitionTo(MigrationSlice.State.IMPLEMENTED)
                .withEvidence("contract fixtures pass")
                .transitionTo(MigrationSlice.State.VERIFIED);

        MigrationSlice routable = new MigrationSlice(
                verified.id(),
                verified.module(),
                verified.capabilities(),
                verified.legacyOwner(),
                verified.targetOwner(),
                verified.writeOwner(),
                verified.state(),
                "route analytics-summary to legacy-jobtrace",
                verified.evidence());

        assertThatThrownBy(() -> routable.transitionTo(MigrationSlice.State.ACTIVE))
                .hasMessageContaining("bridge and rollback");

        MigrationSlice active = routable
                .withEvidence(MigrationSlice.BRIDGE_EVIDENCE)
                .withEvidence(MigrationSlice.ROLLBACK_EVIDENCE)
                .transitionTo(MigrationSlice.State.ACTIVE);
        assertThatThrownBy(() -> active.transitionTo(MigrationSlice.State.OBSERVED))
                .hasMessageContaining("seven-day observation");
    }
}
