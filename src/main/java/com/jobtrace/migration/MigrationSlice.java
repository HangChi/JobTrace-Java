package com.jobtrace.migration;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Records the independently reversible ownership state of one migration slice.
 */
public record MigrationSlice(
        String id,
        Module module,
        Set<String> capabilities,
        String legacyOwner,
        String targetOwner,
        String writeOwner,
        State state,
        String rollbackRoute,
        List<String> evidence) {

    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    public static final String BRIDGE_EVIDENCE = "identity bridge verified";
    public static final String ROLLBACK_EVIDENCE = "rollback exercised";
    public static final String OBSERVATION_EVIDENCE = "seven-day observation complete";

    public MigrationSlice {
        if (id == null || !SLUG.matcher(id).matches()) {
            throw new IllegalArgumentException("Migration slice id must be a lowercase slug");
        }
        module = Objects.requireNonNull(module, "module");
        capabilities = requireNonBlankValues(capabilities, "capabilities");
        legacyOwner = requireNonBlank(legacyOwner, "legacyOwner");
        targetOwner = requireNonBlank(targetOwner, "targetOwner");
        writeOwner = requireNonBlank(writeOwner, "writeOwner");
        state = Objects.requireNonNull(state, "state");
        rollbackRoute = rollbackRoute == null ? "" : rollbackRoute.trim();
        evidence = evidence == null ? List.of() : requireNonBlankValues(evidence, "evidence");

        if (state == State.VERIFIED && evidence.isEmpty()) {
            throw new IllegalArgumentException("Verified migration slices require evidence");
        }
        if (state == State.ACTIVE && rollbackRoute.isBlank()) {
            throw new IllegalArgumentException("Active migration slices require a rollback route");
        }
        if (state == State.ACTIVE && !hasActivationEvidence(evidence)) {
            throw new IllegalArgumentException(
                    "Active migration slices require bridge and rollback evidence");
        }
        if (state == State.OBSERVED && !evidence.contains(OBSERVATION_EVIDENCE)) {
            throw new IllegalArgumentException(
                    "Observed migration slices require seven-day observation evidence");
        }
    }

    public static MigrationSlice planned(
            String id,
            Module module,
            Set<String> capabilities,
            String legacyOwner,
            String targetOwner,
            String writeOwner) {
        return new MigrationSlice(
                id,
                module,
                capabilities,
                legacyOwner,
                targetOwner,
                writeOwner,
                State.PLANNED,
                "",
                List.of());
    }

    public MigrationSlice withEvidence(String item) {
        String evidenceItem = requireNonBlank(item, "evidence");
        var updated = new java.util.ArrayList<>(evidence);
        updated.add(evidenceItem);
        return new MigrationSlice(
                id,
                module,
                capabilities,
                legacyOwner,
                targetOwner,
                writeOwner,
                state,
                rollbackRoute,
                updated);
    }

    public MigrationSlice transitionTo(State target) {
        Objects.requireNonNull(target, "target");
        if (!state.allowedTargets().contains(target)) {
            throw new IllegalStateException("Unsupported migration slice transition: " + state + " -> " + target);
        }
        if (target == State.VERIFIED && evidence.isEmpty()) {
            throw new IllegalStateException("Verified migration slices require evidence");
        }
        if (target == State.ACTIVE && rollbackRoute.isBlank()) {
            throw new IllegalStateException("Active migration slices require a rollback route");
        }
        if (target == State.ACTIVE && !hasActivationEvidence(evidence)) {
            throw new IllegalStateException(
                    "Active migration slices require bridge and rollback evidence");
        }
        if (target == State.OBSERVED && !evidence.contains(OBSERVATION_EVIDENCE)) {
            throw new IllegalStateException(
                    "Observed migration slices require seven-day observation evidence");
        }
        return new MigrationSlice(
                id,
                module,
                capabilities,
                legacyOwner,
                targetOwner,
                writeOwner,
                target,
                rollbackRoute,
                evidence);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static boolean hasActivationEvidence(List<String> evidence) {
        return evidence.contains(BRIDGE_EVIDENCE)
                && evidence.contains(ROLLBACK_EVIDENCE);
    }

    private static List<String> requireNonBlankValues(List<String> values, String field) {
        if (values == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return values.stream().map(value -> requireNonBlank(value, field)).toList();
    }

    private static Set<String> requireNonBlankValues(Set<String> values, String field) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return values.stream()
                .map(value -> requireNonBlank(value, field))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public enum Module {
        APPLICATIONS,
        INTERVIEWS,
        ANALYTICS,
        REMINDERS,
        DATA_TRANSFER,
        JOB_MARKET,
        IDENTITY_ACCESS
    }

    public enum State {
        PLANNED,
        IMPLEMENTED,
        VERIFIED,
        ACTIVE,
        OBSERVED,
        RETIRED,
        ROLLED_BACK;

        private Set<State> allowedTargets() {
            return switch (this) {
                case PLANNED -> EnumSet.of(IMPLEMENTED);
                case IMPLEMENTED -> EnumSet.of(VERIFIED, ROLLED_BACK);
                case VERIFIED -> EnumSet.of(ACTIVE, ROLLED_BACK);
                case ACTIVE -> EnumSet.of(OBSERVED, ROLLED_BACK);
                case OBSERVED -> EnumSet.of(RETIRED, ROLLED_BACK);
                case RETIRED -> EnumSet.noneOf(State.class);
                case ROLLED_BACK -> EnumSet.of(IMPLEMENTED);
            };
        }
    }
}
