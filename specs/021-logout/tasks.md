# Tasks: Compatible Current-Session Logout

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/openapi.yaml](contracts/openapi.yaml)

## Phase 1: Foundation

- [x] T001 Verify the existing origin, cookie and session schema contracts in `src/main/java/com/jobtrace/identityaccess/` against the installed legacy implementation.
- [x] T002 Add exact anonymous POST and non-cacheable handling for `/api/auth/logout` in `SecurityConfig.java` and the identity-access filter.

## Phase 2: User Story 1 — Current-Session Logout (P1)

**Independent test**: Sign in twice, use one signed cookie to log out, confirm only its session disappears, then repeat with missing, tampered and stale cookies.

- [x] T003 [US1] Add cookie verification and expiry tests in `src/test/java/com/jobtrace/identityaccess/LogoutCookieCompatibilityTest.java` before implementation.
- [x] T004 [US1] Add HTTP and PostgreSQL contract/security tests in `src/test/java/com/jobtrace/identityaccess/LogoutIntegrationTest.java` before implementation.
- [x] T005 [US1] Extend `infrastructure/BetterAuthSessionCookie.java` for strict signature verification and legacy cookie expiry.
- [x] T006 [US1] Implement token-only deletion through `application/LogoutSession.java`, `application/LogoutSessionStore.java` and `infrastructure/PostgresLogoutSessionStore.java`.
- [x] T007 [US1] Implement same-origin logout route, best-effort deletion, empty 204 response and bounded metrics in `web/LogoutController.java` and `web/LogoutMetrics.java`.

## Phase 3: Verification and Handoff

- [x] T008 Run representative p95, security, cookie and full local `./mvnw verify -q` checks; record results in `quickstart.md`.
- [x] T009 Document the route boundary and rollback in `docs/migration-slices/logout.md` and update `docs/migration.md`.

## Dependencies

T001–T002 precede the HTTP contract. T003–T004 precede T005–T007. T005–T007 precede T008–T009. Work stays in this branch, with no subagents or CI/CD edits.
