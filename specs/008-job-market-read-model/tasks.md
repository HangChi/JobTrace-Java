# Tasks: Job Market Read Model Migration

**Input**: Design documents from `specs/008-job-market-read-model/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/openapi.yaml`, and `quickstart.md`

**Tests**: Required by the specification and constitution. Write each behavior's tests before implementation and confirm the intended missing-behavior failure. Use synthetic marketplace and owner data only.

**Organization**: Tasks are grouped by independently testable user story. `[P]` means file-level independence, not permission or a requirement to use subagents.

## Phase 1: Setup — Current Job Market Contract

**Purpose**: Establish a trustworthy current-service oracle without production data or Java behavior changes.

- [X] T001 Record current campaign routes, source files, defaults, `favorite=false` omission semantics, closed-projection rules, list/detail ordering, 50-position preview, source preference, unavailable reasons, and intentional no-store hardening in `src/test/resources/contracts/jobmarket/README.md`.
- [X] T002 [P] Add synthetic default, empty, filtered, closed, favorite, synced, directory, unsafe-link, null-time, over-50-position, and two-owner list/detail expectations in `src/test/resources/contracts/jobmarket/read-fixtures.legacy.json`.
- [X] T003 [P] Add a minimal migration-head-compatible test-only schema for job-market companies, projections, campaigns, posts, sources, source records, locations, favorites, applications, and application links in `src/test/resources/postgres/job-market-read-model.sql`; add no production migration or refresh trigger invocation.
- [X] T004 [P] Add parser, path, parameter, response, no-store, and error-schema assertions for both planned GET operations in `src/test/java/com/jobtrace/jobmarket/JobMarketReadOpenApiTest.java` against `specs/008-job-market-read-model/contracts/openapi.yaml`.

**Checkpoint**: Synthetic runtime oracle, isolated legacy-shaped storage, and feature contract validation are ready; no production behavior has changed.

---

## Phase 2: Foundational — Trusted Owner and Shared Read Types

**Purpose**: Establish exact private entry, safe-link policy, immutable projections, and the read-only port before either route is exposed.

**⚠️ CRITICAL**: Complete this phase before user-story implementation.

- [X] T005 [P] Add unit tests for page/limit defaults, derived include-closed selection, HTTPS canonicalization, list application mode, stale-job reason `该岗位已失效`, unsafe-link reason `来源未提供安全的官方投递地址`, and null timestamps in `src/test/java/com/jobtrace/jobmarket/JobMarketReadRulesTest.java`.
- [X] T006 [P] Add bridge regression tests for exact campaign list and UUID detail GET paths, method/path-bound assertions, replay handling, and excluded favorite, sync, admin, and non-UUID paths in `src/test/java/com/jobtrace/identityaccess/BridgeAuthenticationFilterTest.java`.
- [X] T007 Define immutable query, company, location, source, summary, page, job, and detail types plus safe-target rules in `src/main/java/com/jobtrace/jobmarket/domain/MarketplaceQuery.java`, `CampaignCompany.java`, `CampaignLocation.java`, `CampaignSource.java`, `CampaignSummary.java`, `CampaignPage.java`, `CampaignJob.java`, `CampaignDetail.java`, and `ApplyTargetPolicy.java` to satisfy T005; never carry a caller-selected owner.
- [X] T008 Define owner-scoped list and detail operations in `src/main/java/com/jobtrace/jobmarket/application/JobMarketReadQuery.java`; expose no mutation, refresh, synchronization, or caller-owner operation.
- [X] T009 Extend `src/main/java/com/jobtrace/identityaccess/web/BridgeAuthenticationFilter.java` for only the exact list GET and UUID-shaped detail GET paths to satisfy T006; reuse `BridgePrincipalOwner` and keep bridge enablement opt-in.
- [X] T010 [P] Add test-container schema startup, representative insert, SELECT-only connectivity, and Flyway-disabled assertions in `src/test/java/com/jobtrace/jobmarket/JobMarketReadDatabaseTest.java` using `src/test/resources/postgres/job-market-read-model.sql`.

**Checkpoint**: All stories can use one trusted-owner boundary, immutable contract, safe-link rule, isolated schema, and read-only query port.

---

## Phase 3: User Story 1 — Browse Current Recruitment Campaigns (Priority: P1) 🎯 MVP

**Goal**: Return the authenticated user's first/default or explicitly paged company-level marketplace view with current opportunities and isolated favorite state.

**Independent Test**: Seed current, stale, closed, empty, null-time, over-50-position, and two-owner favorite data; request the default page as both owners and verify complete fields, stable order, closed exclusion, preview/count, page metadata, no leakage, no writes, and fixture parity.

### Tests for User Story 1

- [X] T011 [P] [US1] Add structural JSON parity tests for representative default, empty, null-time, and 50-position-preview fixtures in `src/test/java/com/jobtrace/jobmarket/JobMarketListContractTest.java`.
- [X] T012 [P] [US1] Add use-case tests for required trusted owner, default criteria, immutable page propagation, and query failure propagation in `src/test/java/com/jobtrace/jobmarket/ListCampaignsTest.java`.
- [X] T013 [P] [US1] Add PostgreSQL two-owner favorite tests proving identical shared summaries and zero foreign favorite disclosure in `src/test/java/com/jobtrace/jobmarket/JobMarketListOwnerIsolationIntegrationTest.java`.
- [X] T014 [P] [US1] Add PostgreSQL tests for default closed exclusion, company aggregation, publication/confirmation/company ordering, nulls-last behavior, empty/out-of-range pages, exact totals, 50-position preview, complete count, and fixed query count in `src/test/java/com/jobtrace/jobmarket/JobMarketListQueryIntegrationTest.java`.
- [X] T015 [P] [US1] Add HTTP tests for default page/limit, explicit pagination, no-store success, empty page, ordinary-principal rejection, and safe unavailable storage response in `src/test/java/com/jobtrace/jobmarket/JobMarketListControllerTest.java`.

### Implementation for User Story 1

- [X] T016 [US1] Implement trusted-owner default/paged list orchestration in `src/main/java/com/jobtrace/jobmarket/application/ListCampaigns.java` to satisfy T012 without caching personalized results.
- [X] T017 [US1] Implement the parameterized owner-bound company projection, favorite overlay, total, 50-position preview, and stable ordering query in `src/main/java/com/jobtrace/jobmarket/infrastructure/PostgresJobMarketReadQuery.java` to satisfy T013–T014 without invoking projection refresh functions.
- [X] T018 [US1] Expose only `GET /api/job-market/campaigns` with trusted owner, default/paged parameters, `private, no-store`, and safe errors in `src/main/java/com/jobtrace/jobmarket/web/JobMarketReadController.java` to satisfy T011 and T015.
- [X] T019 [US1] Add bounded list operation/outcome/latency metrics without owner, filter, company, campaign, URL, favorite, or returned-content labels in `src/main/java/com/jobtrace/jobmarket/web/JobMarketReadMetrics.java`.
- [X] T020 [US1] Run T011–T015 and record independent MVP fixture parity, favorite isolation, fixed query count, no-store, and read-only evidence in `specs/008-job-market-read-model/validation-report.md`.

**Checkpoint**: The default/paged current campaign list works independently; search/filter behavior and detail are not required for this MVP.

---

## Phase 4: User Story 2 — Find Relevant Opportunities (Priority: P2)

**Goal**: Preserve current keyword, company, location, status, inclusive-date, favorite, validation, combined-filter, total, and stable pagination behavior.

**Independent Test**: Use a deterministic mixed catalog and verify every filter independently and conjunctively, `favorite=true`, `favorite=false`, explicit closed eligibility, inclusive date boundaries, exact totals, stable page boundaries, trimmed/mixed-case/multilingual text, and rejection of malformed or out-of-range input.

### Tests for User Story 2

- [X] T021 [P] [US2] Add parameter normalization tests for blank/trimmed/mixed-case/multilingual text, 100/101-character bounds, strict status/boolean/date values, page/limit limits, and `favorite=false` omission behavior in `src/test/java/com/jobtrace/jobmarket/MarketplaceQueryParametersTest.java`.
- [X] T022 [P] [US2] Add PostgreSQL tests for each filter and combined filters, inclusive `postedFrom`, exact totals, stable multi-page ordering, `favorite=true` owner isolation, explicit closed projection, and inactive-source hiding in `src/test/java/com/jobtrace/jobmarket/JobMarketFilterQueryIntegrationTest.java`.
- [X] T023 [P] [US2] Add HTTP tests for all accepted filters, combined requests, trimmed blanks, malformed values, out-of-range pagination, safe validation problems, and no private echo in `src/test/java/com/jobtrace/jobmarket/JobMarketFilterControllerTest.java`.

### Implementation for User Story 2

- [X] T024 [US2] Implement exact query-string normalization and validation in `src/main/java/com/jobtrace/jobmarket/web/MarketplaceQueryParameters.java`, deriving `includeClosed` inside `src/main/java/com/jobtrace/jobmarket/domain/MarketplaceQuery.java` to satisfy T021.
- [X] T025 [US2] Extend `src/main/java/com/jobtrace/jobmarket/infrastructure/PostgresJobMarketReadQuery.java` with bound conjunctive search, company, location, status, inclusive date, favorite, and include-closed predicates while preserving US1 totals and ordering to satisfy T022.
- [X] T026 [US2] Wire validated filtering into `src/main/java/com/jobtrace/jobmarket/web/JobMarketReadController.java` and `src/main/java/com/jobtrace/jobmarket/application/ListCampaigns.java` without adding filter values to diagnostics to satisfy T023.
- [X] T027 [US2] Run T021–T023 and record filter, validation, closed/favorite, total, stable-pagination, isolation, and no-write parity in `specs/008-job-market-read-model/validation-report.md`.

**Checkpoint**: Browsing and discovery filters are independently verified; campaign detail remains separate.

---

## Phase 5: User Story 3 — Inspect Campaign Jobs (Priority: P3)

**Goal**: Return one eligible company campaign summary with its complete ordered current jobs, safe application state, source attribution, and owner-isolated tracked-application references.

**Independent Test**: Request synced and directory-backed details as two owners; verify complete summary positions, active-source selection, job/location ordering, closed-job exclusion, stale/unsafe reasons, missing/ineligible behavior, tracked-application isolation, bounded queries, and no writes.

### Tests for User Story 3

- [ ] T028 [P] [US3] Add structural JSON parity tests for synced, directory, stale, unsafe-link, missing-link, duplicate-location, and complete-position detail fixtures in `src/test/java/com/jobtrace/jobmarket/JobMarketDetailContractTest.java`.
- [ ] T029 [P] [US3] Add use-case tests for trusted owner, valid/missing/ineligible campaign results, safe not-found behavior, and query failure propagation in `src/test/java/com/jobtrace/jobmarket/GetCampaignDetailTest.java`.
- [ ] T030 [P] [US3] Add PostgreSQL tests for campaign-to-company resolution, current representative summary, complete positions, closed-job exclusion, active/official/recent source selection, deduplicated locations, established job order, unsafe targets, and fixed query count in `src/test/java/com/jobtrace/jobmarket/JobMarketDetailQueryIntegrationTest.java`.
- [ ] T031 [P] [US3] Add two-owner PostgreSQL tests proving tracked-application references are owner-bound and missing/foreign links never alter shared job content in `src/test/java/com/jobtrace/jobmarket/JobMarketDetailOwnerIsolationIntegrationTest.java`.
- [ ] T032 [P] [US3] Add HTTP tests for valid UUID detail, malformed UUID validation, missing/ineligible not-found, no-store success, ordinary-principal rejection, and safe storage failure in `src/test/java/com/jobtrace/jobmarket/JobMarketDetailControllerTest.java`.

### Implementation for User Story 3

- [ ] T033 [US3] Implement trusted-owner detail orchestration and safe not-found mapping in `src/main/java/com/jobtrace/jobmarket/application/GetCampaignDetail.java` to satisfy T029.
- [ ] T034 [US3] Implement campaign resolution, complete summary, set-based eligible-job/location/source selection, owner-bound application links, safe target mapping, and fixed query count in `src/main/java/com/jobtrace/jobmarket/infrastructure/PostgresJobMarketReadQuery.java` to satisfy T030–T031 without N+1 reads.
- [ ] T035 [US3] Expose only `GET /api/job-market/campaigns/{campaignId}` with UUID validation, trusted owner, `private, no-store`, bounded metrics, and safe validation/not-found/dependency errors in `src/main/java/com/jobtrace/jobmarket/web/JobMarketReadController.java` to satisfy T028 and T032.
- [ ] T036 [US3] Run T028–T032 and record independent synced/directory parity, tracked-link isolation, target safety, query bounds, no-store, and read-only evidence in `specs/008-job-market-read-model/validation-report.md`.

**Checkpoint**: All three read stories work independently; the legacy service still owns synchronization, marketplace/favorite/tracking writes, schema, sessions, frontend routing, and production traffic.

---

## Phase 6: Polish and Release Gates

**Purpose**: Prove cross-route security, failure safety, performance, contract convergence, schema ownership, and review readiness without deployment.

- [ ] T037 [P] Add only the protected campaign list/detail GET operations and response schemas to `specs/001-java-migration/contracts/openapi.yaml`; preserve favorite, tracking, sync, admin, public, and mutation exclusions.
- [ ] T038 [P] Add absent, malformed, forged, expired, path-mismatched, method-mismatched, replayed, non-UUID-path, and ordinary-principal denial tests for both reads in `src/test/java/com/jobtrace/jobmarket/JobMarketReadSecurityTest.java`.
- [ ] T039 [P] Add safe storage-outage, request-ID, log-redaction, no-private-label metrics, partial-result rejection, and no-store tests for both reads in `src/test/java/com/jobtrace/jobmarket/JobMarketReadFailureAndMetricsTest.java`.
- [ ] T040 [P] Add a reproducible 100-company/100,000-job/two-owner test with 10 warm-ups, 40 list and 40 detail samples, both p95 ≤500 ms including serialization, and fixed SQL query counts in `src/test/java/com/jobtrace/jobmarket/JobMarketReadPerformanceTest.java`.
- [ ] T041 [P] Assert no production migration or Flyway activation, refresh-function call, non-GET job-market handler, favorite/tracking write, sync/admin/collector integration, frontend route change, or database row/schema mutation in `src/test/java/com/jobtrace/jobmarket/JobMarketReadOnlySurfaceTest.java` and `src/test/java/com/jobtrace/migration/LegacySchemaSafetyTest.java`.
- [ ] T042 [P] Add architecture rules preserving the `jobmarket` web-to-application-to-domain dependency direction, infrastructure port implementation, and freedom from applications/reminders/datatransfer infrastructure cycles in `src/test/java/com/jobtrace/ArchitectureTest.java`.
- [ ] T043 [P] Document legacy synchronization/writer/session/schema ownership, Java read-only consumption, no-store hardening, safe-link policy, performance dataset, and no-deployment decision in `docs/migration-slices/job-market-read-model.md` and `docs/migration.md`.
- [ ] T044 Run `./mvnw verify` with Docker and record fixture parity, two-owner isolation, feature/repository OpenAPI, security/failure behavior, read-only/schema checks, architecture, Checkstyle, at least 80% line/branch coverage, p95/query counts, and Java-only artifact evidence in `specs/008-job-market-read-model/validation-report.md`.
- [ ] T045 Review the result against `spec.md` and all four constitution principles, record Solo-Maintainer Mode self-review in `specs/008-job-market-read-model/validation-report.md`, open a PR to `main`, pass required CI/security checks, and record its URL and explicitly unscheduled deployment status.

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup provides the runtime oracle, synthetic fixtures, test-only schema, and feature-contract parser.
- Foundational follows Setup and blocks all three user stories.
- US1 is the independently releasable read-only MVP.
- US2 extends US1's criteria, query, use case, and controller, so it follows US1 in the single-maintainer workflow.
- US3 can be tested from the shared foundation but follows US2 to avoid overlapping adapter/controller edits.
- Polish follows all selected stories and blocks PR merge, not production deployment.

### User Story Dependency Graph

```text
Setup → Foundational → US1 (MVP) → US2 → US3 → Polish/PR
```

US3 has no behavioral dependency on US2, but the recommended order serializes changes to `PostgresJobMarketReadQuery.java` and `JobMarketReadController.java` for one maintainer.

### Within Each User Story

1. Add synthetic fixture/contract, use-case, storage/isolation, and HTTP tests first; observe the intended missing-behavior failure.
2. Add or extend immutable domain rules, then the application use case, parameterized storage adapter, and HTTP composition.
3. Verify the story's independent checkpoint before proceeding.
4. Keep all synchronization, mutation, schema, session, frontend-routing, deployment, and traffic authority in the current service.

### Parallel Opportunities

- T002–T004 target independent fixture, schema, and contract-test files.
- T005, T006, and T010 target independent foundational tests; T007 follows T005 and T009 follows T006.
- Each story's `[P]` tests target separate files and can be prepared independently before implementation.
- T037–T043 mostly target separate contract, test, architecture, and documentation files; shared-file edits should be serialized.
- The user is a solo developer and requested no subagents. `[P]` records file independence only and does not call for delegation.

## Parallel Examples

### User Story 1

```text
T011 JobMarketListContractTest.java
T012 ListCampaignsTest.java
T013 JobMarketListOwnerIsolationIntegrationTest.java
T014 JobMarketListQueryIntegrationTest.java
T015 JobMarketListControllerTest.java
```

### User Story 2

```text
T021 MarketplaceQueryParametersTest.java
T022 JobMarketFilterQueryIntegrationTest.java
T023 JobMarketFilterControllerTest.java
```

### User Story 3

```text
T028 JobMarketDetailContractTest.java
T029 GetCampaignDetailTest.java
T030 JobMarketDetailQueryIntegrationTest.java
T031 JobMarketDetailOwnerIsolationIntegrationTest.java
T032 JobMarketDetailControllerTest.java
```

## Implementation Strategy

1. Complete Setup and Foundational, then implement US1 as the smallest useful read-only marketplace MVP.
2. Stop and validate US1 independently before extending the same list path with US2 filters.
3. Add US3 detail as a separate use case and independent contract, preserving fixed set-based query counts.
4. Complete cross-cutting gates and PR review before merge. Do not deploy or route production traffic.
5. Favorite mutation, tracking/application creation, synchronization/admin migration, schema ownership, frontend changes, deployment, canary, and rollback rehearsal require separately authorized future work.

## Notes

- Every task follows `- [ ] T### [P?] [US?] Description with file path`.
- `[P]` means different files and no dependency on another incomplete task in the same phase.
- Tests are written before the behavior they prove and must fail for the intended missing behavior.
- Commit after each task or cohesive task group; stop at any checkpoint to validate independently.
