# Tasks: Job Market Operations

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/openapi.yaml](contracts/openapi.yaml)

**Tests**: Required by FR-009 and repository coverage gates. Work is sequential in one agent; `[P]` only describes independent files, not delegated work.

## Phase 1: Setup

- [x] T001 Record legacy route, schema and security decisions in `specs/027-job-market-operations/research.md` and `contracts/openapi.yaml`
- [x] T002 Verify existing jobmarket module, dependencies and ignore patterns in `src/main/java/com/jobtrace/jobmarket/`, `pom.xml` and `.gitignore`

## Phase 2: Foundation

- [x] T003 Verify trusted owner/admin bridge and shared problem responses in `src/main/java/com/jobtrace/identityaccess/web/` and `src/main/java/com/jobtrace/shared/web/`
- [x] T004 Define source registration, update and health contracts in `src/main/java/com/jobtrace/jobmarket/domain/` and the store port in `application/`

## Phase 3: User Story 1 — Favorites (P1)

**Goal**: Idempotent, owner-scoped favorite mutation reflected in current list/detail reads.

**Independent Test**: Two owners favorite/unfavorite the same company; only their own projection changes.

- [x] T005 [US1] Add favorite HTTP and PostgreSQL tests in `src/test/java/com/jobtrace/jobmarket/JobMarketFavoriteIntegrationTest.java`
- [x] T006 [US1] Implement favorite command port and use case in `src/main/java/com/jobtrace/jobmarket/application/`
- [x] T007 [US1] Implement owner-scoped SQL in `src/main/java/com/jobtrace/jobmarket/infrastructure/PostgresCampaignFavoriteCommand.java`
- [x] T008 [US1] Add compatible PUT/DELETE route in `src/main/java/com/jobtrace/jobmarket/web/JobMarketFavoriteController.java`

## Phase 4: User Story 2 — Curation (P2)

**Goal**: Administrators manage trusted catalog and reviewable candidates.

**Independent Test**: Admin creates a source and reviews candidates; ordinary users cannot change the catalog.

- [x] T009 [US2] Add source contract and authorization tests in `src/test/java/com/jobtrace/jobmarket/JobMarketSourceAdminIntegrationTest.java` and `SourceRegistrationTest.java`
- [x] T010 [US2] Implement source CRUD use case and store in `src/main/java/com/jobtrace/jobmarket/application/ManageJobMarketSources.java` and `infrastructure/PostgresSourceCatalogStore.java`
- [x] T011 [US2] Add administrator source list/create/update routes in `src/main/java/com/jobtrace/jobmarket/web/JobMarketSourceAdminController.java`
- [ ] T012 [US2] Add default catalog and bootstrap tests in `src/test/java/com/jobtrace/jobmarket/JobMarketCatalogAdminIntegrationTest.java`
- [ ] T013 [US2] Implement administrator catalog and bootstrap routes in `src/main/java/com/jobtrace/jobmarket/web/` and `application/`
- [ ] T014 [US2] Add source/company candidate review and discovery tests in `src/test/java/com/jobtrace/jobmarket/JobMarketCandidateIntegrationTest.java`
- [ ] T015 [US2] Implement candidate contracts, persistence, review and routes in `src/main/java/com/jobtrace/jobmarket/domain/`, `application/`, `infrastructure/` and `web/`

## Phase 5: User Story 3 — Intake and sync (P3)

**Goal**: Safely collect external leads, synchronize approved sources and inspect run history.

**Independent Test**: Fake external pages and a fake adapter produce reviewable candidates and fenced run records; unsafe URLs are refused.

- [ ] T016 [US3] Add URL/DNS/redirect and bounded-page tests in `src/test/java/com/jobtrace/jobmarket/JobMarketSourceFetchSecurityTest.java`
- [ ] T017 [US3] Implement safe external client and ATS/WeChat parsers in `src/main/java/com/jobtrace/jobmarket/infrastructure/`
- [ ] T018 [US3] Implement company research and ATS/WeChat intake use cases and routes in `src/main/java/com/jobtrace/jobmarket/application/` and `web/`
- [ ] T019 [US3] Add lease, fencing, retry and run-history integration tests in `src/test/java/com/jobtrace/jobmarket/JobMarketSyncIntegrationTest.java`
- [ ] T020 [US3] Implement run contracts, source synchronization and run-history query in `src/main/java/com/jobtrace/jobmarket/domain/`, `application/` and `infrastructure/`
- [ ] T021 [US3] Add admin and authenticated internal sync routes in `src/main/java/com/jobtrace/jobmarket/web/`

## Phase 6: Polish

- [ ] T022 Document Java/legacy boundaries in `docs/migration-slices/job-market-operations.md` and `docs/migration.md`
- [ ] T023 Run `./mvnw verify`, inspect changed-code coverage and record representative timings in `specs/027-job-market-operations/quickstart.md`

## Dependencies and execution

T001–T002 precede foundation. T003 precedes US1; T004 precedes US2 and US3. US1 is the minimum viable increment; US2 and US3 remain independently testable but US3 uses approved sources from US2. Tests precede corresponding production code. No sub-agents, frontend work, deployment or CI/CD changes are in scope.
