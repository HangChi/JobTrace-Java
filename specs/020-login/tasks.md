# Tasks: Compatible Account Login

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/openapi.yaml](contracts/openapi.yaml)

## Phase 1: Foundation

- [x] T001 Reuse the 019 origin and challenge boundaries in `src/main/java/com/jobtrace/identityaccess/` and add a login-specific ten-per-minute limiter.
- [x] T002 Add exact anonymous POST and no-store handling for `/api/auth/login` in `SecurityConfig.java` and the identity-access filter.

## Phase 2: User Story 1 — Compatible Login (P1)

**Independent test**: Login with username and verified email, inspect the stored session and cookie, then verify rejected identities create neither.

- [x] T003 [US1] Add use-case, cookie-vector, database, and HTTP contract/security tests in `src/test/java/com/jobtrace/identityaccess/`.
- [x] T004 [US1] Implement validation, credential checks, generic denials, and safe redirect selection in `application/LoginAccount.java` and supporting ports/domain types.
- [x] T005 [US1] Implement credential lookup and session persistence in `infrastructure/PostgresLoginStore.java`.
- [x] T006 [US1] Implement Better Auth-compatible signed cookie issuance in `infrastructure/BetterAuthSessionCookie.java`.
- [x] T007 [US1] Implement login preflight, web mapping, failure responses, and bounded metrics in `application/` and `web/`.

## Phase 3: Verification and Handoff

- [x] T008 Run representative p95, security, cookie and full local `./mvnw verify -q` checks; record results in `quickstart.md`.
- [x] T009 Document the route boundary and rollback in `docs/migration-slices/login.md` and update `docs/migration.md`.

## Dependencies

T001–T002 precede the HTTP contract. T004–T007 precede T008–T009. All work stays in this branch and uses no subagents or CI/CD edits.
