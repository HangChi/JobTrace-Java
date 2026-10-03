# Validation Report: Administrator Access Changes

**Validated**: 2026-10-03

**Branch**: `codex/010-admin-access-change`

## Results

- Administration, bridge, and architecture focused run: 75 tests, with the only initial failure caused by an unquoted OpenAPI flow-map description; the corrected contract tests pass.
- `./mvnw verify`: 339 tests, 0 failures, 0 errors, 1 skipped.
- JaCoCo: all configured line and branch coverage checks met across 191 classes.
- Checkstyle: 0 violations using the repository-configured ruleset.
- Reproducible PostgreSQL replay benchmark: 40 measured calls after warm-up, p95 0 ms, below the 1-second write budget.

## Durable Behavior Evidence

- Promote, demote, disable, and enable each increment access version once and append one audit event.
- Disable deletes the target's sessions; enable does not recreate them.
- Stale version, incompatible state, missing self-confirmation, and last-active-administrator protections leave access unchanged and return stable codes.
- Sequential and concurrent identical requests converge on one transition and one audit event; changed-payload reuse conflicts.
- Missing targets and no-longer-current administrators fail safely.

## Scope Checks

- The only added mutation route is `PATCH /api/admin/users/{id}`.
- Java invokes the established `change_user_access_as` operation and contains no duplicate user update, session delete, or audit insert SQL.
- No production migration, frontend, job-market administration, deployment, routing, or CI/CD configuration was changed.
