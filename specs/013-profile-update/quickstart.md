# Quickstart: Account Profile Update

## Focused validation

```bash
./mvnw -Dtest='com.jobtrace.identityaccess.*Profile*Test' test
```

Expected: normalization, avatar states, owner isolation, response parity, security, failure, metrics, and performance tests pass.

Recorded on 2026-10-03: 16 tests passed with zero failures, errors, or skips. The representative performance fixture recorded a 0 ms p95 and exactly one durable mutation.

## Whole-repository validation

```bash
./mvnw verify
```

Expected: all tests, coverage, architecture, Checkstyle, frontend build, and packaging checks pass.

Recorded on 2026-10-03: `./mvnw verify` completed successfully with 385 tests, zero failures, zero errors, and one existing skip. All 25 architecture rules passed, JaCoCo coverage checks were met, and Checkstyle reported zero violations.

## Manual contract

Send `PATCH /api/profile` with a PATCH-bound assertion and `{ "displayName": " Example ", "image": null }`. Expect the normalized owner profile with unchanged recovery-email fields.

## Rollback

Keep the legacy PATCH as production owner. If later activation fails, route PATCH back to the legacy implementation; no schema rollback is necessary.
