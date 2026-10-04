# Validation Guide: Complete Account Authentication

1. Run `./mvnw verify` from the repository root. Use the existing Testcontainers PostgreSQL fixture and a local test-only auth secret; no production database is required.
2. Sign in through `POST /api/auth/login`, retain the signed cookie, and call `GET /api/auth/session`. Confirm a non-cacheable current-user response with no token or credential fields.
3. Use the same cookie on owner-scoped and administrator routes. Confirm owner isolation and current role checks. Revoke the session, disable the account, tamper with the cookie, and repeat; every protected request must then fail.
4. Attempt a cookie-authenticated write with a foreign or missing origin. Confirm no mutation. Repeat with an exact trusted origin.
5. Request password reset for known and unknown recovery addresses. Confirm identical public responses; follow a known-account link, reset once, and verify the old password and prior sessions fail. Retry the token and wait beyond one hour in a controlled-clock test.
6. Upload supported images at the limit and invalid/oversized images. Confirm only valid content reaches the storage adapter and the returned URL is owner-scoped.

Expected gate: all tests pass, changed production code meets 80% line and branch coverage, and representative p95 measurements meet [spec.md](spec.md).

## Validation result (2026-10-05)

`./mvnw verify -q -Dlogging.level.root=ERROR` passed: 593 tests, 0 failures, 0 errors, 1 skipped. The configured Checkstyle and JaCoCo gates passed; aggregate line coverage was 4660/4913 (94.85%) and branch coverage was 1846/2293 (80.51%). No real mail or COS upload was sent during tests.

HTTP, database and unit tests cover signed-cookie revocation/expiry/owner and role checks, exact-origin browser writes, known/unknown recovery responses, legacy hashed token identifiers, one-hour expiry, one-use consumption, credential update with session revocation, mail and COS adapter failure paths, supported image signatures and the 5 MiB boundary. A local 20-operation Testcontainers/MockMvc sample measured current-session reads at 0 ms p95 and password-reset writes at 74 ms p95, under the 500 ms and 1 s budgets respectively. These are local lab measurements, not production telemetry.
