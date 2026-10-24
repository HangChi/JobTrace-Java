# Validation Report: Account Session Read Model

**Validated**: 2026-10-03

**Branch**: `codex/011-session-read-model`

## Results

- Focused account-session run: 15 tests, 0 failures, 0 errors, 0 skipped.
- `./mvnw verify`: 354 tests, 0 failures, 0 errors, 1 skipped.
- JaCoCo: all configured line and branch coverage checks met across 197 classes.
- Checkstyle: 0 violations using the repository-configured ruleset.
- Reproducible PostgreSQL benchmark: 500 active owned sessions, one query per call, 40 measured calls after warm-up, p95 1 ms, below the 500 ms read budget.

## Contract and Security Evidence

- The response matches the synthetic legacy array contract, including UTC millisecond timestamps, nullable user agent, and current-device marker.
- Expired and foreign-owner sessions are excluded; an unmatched or foreign current-session reference marks no item and discloses no foreign row.
- Absent, ordinary, expired, forged, path-mismatched, and replayed identities fail closed.
- Missing, blank, and overlong current-session metadata returns safe validation problems.
- Successful and failed surface responses use `Cache-Control: private, no-store`.

## Scope Checks

- The only added route is `GET /api/profile/sessions`; DELETE and PATCH remain unsupported.
- The adapter issues one parameterized SELECT and contains no insert, update, delete, lock, refresh, or revocation behavior.
- Better Auth and the existing service remain session and schema authorities.
- No frontend, deployment, routing, production traffic, or CI/CD configuration was changed.
