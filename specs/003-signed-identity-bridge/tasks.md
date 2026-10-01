---
description: "Implementation tasks for the signed identity bridge and analytics activation"
---

# Tasks: Signed Identity Bridge and Analytics Activation

**Input**: Design documents from `specs/003-signed-identity-bridge/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/identity-bridge.md`

**Tests**: Security, contract, integration, owner-isolation, replay-race, performance, and rollback
tests are mandatory for this feature.

**Organization**: Tasks are grouped by independently testable user story. The sole maintainer works
sequentially even where `[P]` identifies file-level independence; no sub-agent execution is assumed.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Introduce disabled-by-default dependencies and environment contracts without changing
production authentication or traffic.

- [x] T001 Add the Boot-managed OAuth2 JOSE resource-server and Spring Data Redis dependencies in `pom.xml`
- [x] T002 [P] Add disabled bridge, issuer, audience, lifetime, skew, key-map, and replay-store settings to `src/main/resources/application.yaml`
- [x] T003 [P] Add safe placeholder bridge and replay-store variables to `.env.example` and document secret handling in `docs/configuration.md`
- [x] T004 [P] Add a local-only Valkey service and health check to `compose.yaml` without changing PostgreSQL or Flyway ownership

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Define validated configuration, identity types, replay port, and failure taxonomy used by
all bridge stories.

**Critical**: No user-story implementation starts until this phase is complete.

- [x] T005 Write failing bridge configuration validation tests in `src/test/java/com/jobtrace/shared/config/JobTracePropertiesTest.java`
- [x] T006 Extend validated bridge and replay configuration records in `src/main/java/com/jobtrace/shared/config/JobTraceProperties.java`
- [x] T007 [P] Define the immutable validated identity model in `src/main/java/com/jobtrace/identityaccess/domain/BridgeIdentity.java`
- [x] T008 [P] Define atomic replay-claim and availability ports in `src/main/java/com/jobtrace/identityaccess/domain/ReplayGuard.java`
- [x] T009 [P] Define bounded authentication failure classifications in `src/main/java/com/jobtrace/identityaccess/domain/BridgeAuthenticationFailure.java`
- [x] T010 Add architecture rules for the `identityaccess` module and dependency direction in `src/test/java/com/jobtrace/ArchitectureTest.java`

**Checkpoint**: Configuration and domain contracts exist, but bridge authentication remains disabled.

---

## Phase 3: User Story 1 - Seamless authenticated analytics access (Priority: P1) MVP

**Goal**: A valid legacy-issued assertion establishes the correct Java principal and returns the
existing owner-isolated analytics response.

**Independent Test**: Generate a valid v1 fixture for a synthetic owner, invoke
`GET /api/analytics/summary`, and compare the response with the existing legacy fixture while direct
public identity headers and absent assertions remain unauthorized.

### Tests for User Story 1

- [x] T011 [P] [US1] Add deterministic current/previous key and valid-token test support in `src/test/java/com/jobtrace/identityaccess/BridgeTokenFixtures.java`
- [x] T012 [P] [US1] Write valid assertion and principal-mapping tests in `src/test/java/com/jobtrace/identityaccess/BridgeTokenVerifierTest.java`
- [x] T013 [P] [US1] Write filter-level valid, absent, and public-header rejection tests in `src/test/java/com/jobtrace/identityaccess/BridgeAuthenticationFilterTest.java`
- [x] T014 [US1] Add a signed-bridge owner-isolation case to `src/test/java/com/jobtrace/analytics/AnalyticsSummarySecurityTest.java`

### Implementation for User Story 1

- [x] T015 [US1] Parse and validate the exact v1 JWS contract in `src/main/java/com/jobtrace/identityaccess/web/BridgeTokenVerifier.java`
- [x] T016 [US1] Coordinate verification and one-time replay claim in `src/main/java/com/jobtrace/identityaccess/application/ClaimAssertionUseCase.java`
- [x] T017 [US1] Establish the Spring Security principal only after complete validation in `src/main/java/com/jobtrace/identityaccess/web/BridgeAuthenticationFilter.java`
- [x] T018 [US1] Register the bridge filter only for the analytics summary and preserve fail-closed defaults in `src/main/java/com/jobtrace/shared/security/SecurityConfig.java`
- [x] T019 [US1] Make request-ID normalization ordering explicit for request binding in `src/main/java/com/jobtrace/shared/web/RequestIdFilter.java`
- [x] T020 [US1] Verify the Java endpoint against the frozen v1 contract in `src/test/java/com/jobtrace/testing/OpenApiContractTest.java` and `specs/003-signed-identity-bridge/contracts/identity-bridge.md`

**Checkpoint**: Java can authenticate a synthetic valid assertion without changing browser routing.

---

## Phase 4: User Story 2 - Reject forged, stale, or replayed identity (Priority: P2)

**Goal**: Every malformed, forged, stale, misbound, unknown-key, duplicate, or unavailable-store
case fails closed, including concurrent and cross-replica replay.

**Independent Test**: Run the negative matrix and a concurrent double-use test; exactly one valid
first use succeeds and every invalid or later use returns the same non-disclosing 401 response.

### Tests for User Story 2

- [x] T021 [P] [US2] Add the full malformed header, claim, algorithm, signature, time, method, path, request-ID, role, access-version, and size matrix to `src/test/java/com/jobtrace/identityaccess/BridgeTokenVerifierTest.java`
- [x] T022 [P] [US2] Add Valkey atomic first-use, duplicate, expiry, race, and outage tests in `src/test/java/com/jobtrace/identityaccess/RedisReplayGuardIntegrationTest.java`
- [x] T023 [P] [US2] Add current/previous/retired/unknown key rotation tests in `src/test/java/com/jobtrace/identityaccess/BridgeKeyRotationTest.java`
- [x] T024 [US2] Add non-disclosing 401 and safe-log assertions in `src/test/java/com/jobtrace/identityaccess/BridgeAuthenticationFilterTest.java`

### Implementation for User Story 2

- [x] T025 [US2] Implement atomic set-if-absent replay claims with bounded TTL in `src/main/java/com/jobtrace/identityaccess/infrastructure/RedisReplayGuard.java`
- [x] T026 [US2] Add a replay-store readiness contributor that fails only when the bridge is enabled in `src/main/java/com/jobtrace/shared/health/BridgeReplayHealthIndicator.java`
- [x] T027 [US2] Add sanitized authentication counters and timings in `src/main/java/com/jobtrace/identityaccess/web/BridgeAuthenticationMetrics.java`
- [x] T028 [US2] Add uniform bridge authentication entry-point responses in `src/main/java/com/jobtrace/shared/security/BridgeAuthenticationEntryPoint.java`
- [x] T029 [US2] Add bridge validation and replay p95 coverage to `src/test/java/com/jobtrace/performance/ReadPerformanceTest.java`

**Checkpoint**: Java verifier is production-code complete and fail-closed, but legacy still owns all
production analytics traffic.

---

## Phase 5: User Story 3 - Controlled analytics canary and rollback (Priority: P3)

**Goal**: The legacy server issues v1 assertions internally, a bounded canary is observable and
reversible, and activation requires seven clean days plus recorded approval.

**Independent Test**: In a non-public environment, request analytics through the legacy route,
compare legacy and Java results, rotate a key, force a rollback signal, and restore all traffic to
legacy within five minutes without database changes.

### Tests for User Story 3

- [x] T030 [P] [US3] Add legacy issuer claim, lifetime, rotation, and no-secret-log tests in `../JobTrace/tests/unit/identity-access/identity-bridge.test.ts`
- [x] T031 [P] [US3] Add legacy route fallback, Java canary, parity, timeout, and rollback contract tests in `../JobTrace/tests/contract/analytics-summary-bridge.contract.test.ts`
- [x] T032 [US3] Add cross-service valid, forged, replayed, and key-rotation fixtures to `src/test/resources/contracts/identity-bridge/` and verify them in `src/test/java/com/jobtrace/migration/IdentityBridgeContractTest.java`

### Implementation for User Story 3

- [x] T033 [US3] Read current disabled state, role, and access version after session validation in `../JobTrace/src/modules/identity-access/infrastructure/identity-bridge-actor.server.ts`
- [x] T034 [US3] Issue request-bound v1 assertions with the active external key in `../JobTrace/src/modules/identity-access/infrastructure/identity-bridge.server.ts`
- [x] T035 [US3] Add disabled-by-default Java canary routing and immediate legacy fallback in `../JobTrace/src/app/api/analytics/summary/route.ts`
- [x] T036 [US3] Document deployment variables, internal ingress, alert thresholds, key rotation, and five-minute rollback in `docs/operations.md`
- [x] T037 [US3] Update analytics slice states, evidence requirements, and seven-day observation log in `docs/migration-slices/analytics-summary.md`
- [x] T038 [US3] Extend the lifecycle test so ACTIVE/OBSERVED requires bridge, rollback, and observation evidence in `src/test/java/com/jobtrace/migration/MigrationSliceTest.java`

**Checkpoint**: A reviewed non-public canary can start. The task stays incomplete until the full
seven-day production observation and explicit activation decision finish.

---

## Phase 6: Polish and Release Gates

- [x] T039 [P] Update architecture and migration trust-boundary documentation in `docs/architecture.md` and `docs/migration.md`
- [x] T040 Run Java `./mvnw verify` and record coverage, static-analysis, and test evidence in `specs/003-signed-identity-bridge/validation-report.md`
- [x] T041 Run legacy `npm run lint`, `npm test`, and `npm run build` after isolating 003 changes from the existing dirty working tree and record evidence in `specs/003-signed-identity-bridge/validation-report.md`
- [x] T042 Execute the rotation, cross-service, performance, and rollback scenarios from `specs/003-signed-identity-bridge/quickstart.md`
- [x] T043 Open a pull request, pass all CI/security checks, and record the Solo-Maintainer Mode self-review against the specification and four constitution principles
- [ ] T044 Observe the production canary for seven consecutive clean days and record zero mismatches, security incidents, or budget breaches in `specs/003-signed-identity-bridge/validation-report.md`
- [ ] T045 Record the explicit production-active or rolled-back decision and final traffic owner in `docs/migration-slices/analytics-summary.md`

---

## Dependencies and Execution Order

- Phase 1 precedes Phase 2; both precede every user story.
- US1 establishes successful authentication and is the MVP.
- US2 hardens the same path and must complete before any canary.
- US3 depends on US1 and US2 and spans both repositories; legacy changes must use a clean branch or
  worktree so the current unrelated modifications are preserved.
- T032 follows T030–T031 because fixtures must prove the real issuer/verifier contract.
- T036–T038 may proceed while the legacy issuer is implemented, but T042 requires all US3 code.
- T044 necessarily takes at least seven calendar days after canary start. T045 is last.

## Parallel Opportunities

- T002–T004 touch independent setup files.
- T007–T009 define independent foundation types.
- Test skeletons T011–T013 and T021–T023 occupy separate files.
- Documentation T036–T037 and T039 can proceed independently of code after the contract is frozen.
- The sole maintainer should still execute and review these sequentially as requested.

## Implementation Strategy

### First safe increment

Complete T001–T020. This yields a disabled-by-default Java verifier with a valid happy path and no
production traffic change.

### Security-complete Java increment

Complete T021–T029. This yields negative coverage, shared replay protection, safe diagnostics, and
readiness behavior suitable for non-public integration.

### Cross-repository canary increment

Complete T030–T043 only after isolating legacy-repository work. Start T044 after release approval;
finish T045 only after the real observation window.

## Format Validation

All 45 tasks use the required checkbox, sequential ID, optional `[P]`, required user-story label in
story phases, and exact repository-relative file path.
