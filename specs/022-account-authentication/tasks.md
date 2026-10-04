# Tasks: Complete Account Authentication

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/openapi.yaml](contracts/openapi.yaml)

## Phase 1: Setup

- [x] T001 Verify installed legacy cookie, session, reset-token, mail and avatar contracts in `specs/022-account-authentication/research.md`.
- [x] T002 Document independent HTTP acceptance and security boundaries in `specs/022-account-authentication/contracts/openapi.yaml` and `quickstart.md`.

## Phase 2: Foundation

- [x] T003 Add failing current-session cookie and database tests in `src/test/java/com/jobtrace/identityaccess/BrowserSessionReadTest.java`.
- [x] T004 Add browser session query port and PostgreSQL adapter in `src/main/java/com/jobtrace/identityaccess/application/BrowserSessionQuery.java` and `infrastructure/PostgresBrowserSessionQuery.java`.
- [x] T005 Add safe current-session endpoint in `src/main/java/com/jobtrace/identityaccess/web/BrowserSessionController.java`.

## Phase 3: User Story 1 — Browser Session Access (P1)

**Independent test**: A valid signed cookie reaches only its owner's protected data; revocation, expiry, disablement and role loss are effective on the next request.

- [x] T006 [US1] Add dual-credential, owner-isolation and CSRF regression tests in `src/test/java/com/jobtrace/identityaccess/BrowserAuthenticationIntegrationTest.java`.
- [x] T007 [US1] Implement distinct verified browser principal and security filter in `src/main/java/com/jobtrace/identityaccess/domain/BrowserIdentity.java` and `web/BrowserAuthenticationFilter.java`.
- [x] T008 [US1] Wire browser and bridge authentication without weakening either route in `src/main/java/com/jobtrace/shared/security/SecurityConfig.java` and `identityaccess/infrastructure/BrowserIdentityAccessConfiguration.java`.
- [x] T009 [US1] Resolve owner, administrator and current-session identity in `src/main/java/com/jobtrace/identityaccess/web/BridgePrincipalOwner.java`, `BridgePrincipalAdmin.java` and affected controllers.
- [x] T010 [US1] Add authenticated profile read and tests in `src/main/java/com/jobtrace/identityaccess/web/AccountProfileReadController.java` and `src/test/java/com/jobtrace/identityaccess/AccountProfileReadTest.java`.

## Phase 4: User Story 2 — Password Recovery (P2)

**Independent test**: The same public response is returned for known and unknown addresses; a valid reset link changes the password once and revokes earlier sessions.

- [x] T011 [US2] Add request, link and single-use reset tests in `src/test/java/com/jobtrace/identityaccess/PasswordRecoveryIntegrationTest.java`.
- [x] T012 [US2] Implement rate-limited, non-enumerating reset issuance and delivery in `src/main/java/com/jobtrace/identityaccess/application/RequestPasswordReset.java` and `infrastructure/PostgresPasswordResetStore.java`.
- [x] T013 [US2] Implement atomic token consumption, password update and session revocation in `src/main/java/com/jobtrace/identityaccess/application/ResetPassword.java` and `infrastructure/PostgresPasswordResetStore.java`.
- [x] T014 [US2] Add exact request, callback and consume routes in `src/main/java/com/jobtrace/identityaccess/web/PasswordRecoveryController.java`.

## Phase 5: User Story 3 — Profile and Avatar (P3)

**Independent test**: Only a valid owner can upload supported images no larger than 5 MiB; invalid bytes never reach storage.

- [x] T015 [US3] Add signature, size, owner and storage-failure tests in `src/test/java/com/jobtrace/identityaccess/AvatarUploadTest.java`.
- [x] T016 [US3] Implement bounded avatar validation and upload use case in `src/main/java/com/jobtrace/identityaccess/application/UploadAvatar.java`.
- [x] T017 [US3] Implement COS adapter and multipart route in `src/main/java/com/jobtrace/identityaccess/infrastructure/TencentCosAvatarStore.java` and `web/AvatarController.java`.

## Phase 6: Polish and verification

- [x] T018 Verify security, compatibility, p95 and full `./mvnw verify` gates; record results in `specs/022-account-authentication/quickstart.md`.
- [x] T019 Document route ownership and rollback in `docs/migration-slices/account-authentication.md` and `docs/migration.md`.

## Dependencies

T003–T005 establish a safe read-only browser session lookup. T006–T010 extend that proof to protected routes. T011–T014 and T015–T017 are independently testable once browser authentication exists. T018–T019 close the stage. No subagents, CI/CD, deployment or production routing edits are part of this task list.

## Implementation strategy

Deliver the current-session read first, then protected browser access, password recovery and avatar upload. Each story has its own tests and remains reversible without schema changes.
