# Quickstart: Account Session Revocation

## Focused validation

```bash
./mvnw -Dtest='com.jobtrace.identityaccess.*Session*Test' test
```

Expected: feature 011 reads remain compatible and feature 012 revocation, ownership, concurrency, security, failure, metrics, and performance tests pass.

## Whole-repository validation

```bash
./mvnw verify
```

Expected: all tests, JaCoCo, Checkstyle, architecture, and packaging checks pass.

## Validation evidence

Validated on 2026-10-03:

- Focused suite: 30 tests, 0 failures, 0 errors, 0 skipped.
- Representative session-revocation p95: 0 ms with one durable mutation per request.
- Whole repository: 369 tests, 0 failures, 0 errors, 1 existing skipped packaging test.
- JaCoCo checks met, 25 architecture rules passed, and Checkstyle reported 0 violations.

## Manual contract

Send `DELETE /api/profile/sessions` with a DELETE-bound signed bridge assertion, matching request ID, `Content-Type: application/json`, and `{ "id": "owned-session-id" }`. Expect `{ "revoked": true }`. Repeat the same request with a fresh assertion and expect a private, non-cacheable not-found problem.

## Rollback

Keep the legacy DELETE as production owner. If later activation fails, route DELETE back to the legacy implementation; no schema rollback is necessary.
