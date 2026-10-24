# Tasks: Job Market Operations

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/openapi.yaml](contracts/openapi.yaml)

**Tests**: Required by FR-007 and repository coverage gates. Work is sequential in one agent; `[P]` only describes independent files, not delegated work. Revised scope: use already persisted remote data; no Java crawler, bootstrap or synchronization worker.

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
- [x] T012 [US2] Test live shared-database catalog search, pagination and no ingestion side effects in `src/test/java/com/jobtrace/jobmarket/JobMarketCatalogAdminIntegrationTest.java`
- [x] T013 [US2] Read catalog from `job_market_companies`, `job_market_sources` and directory campaigns in `src/main/java/com/jobtrace/jobmarket/infrastructure/PostgresJobMarketCatalogQuery.java`
- [x] T014 [US2] Test source/company candidate listing, review, authorization and retries in `src/test/java/com/jobtrace/jobmarket/JobMarketCandidateIntegrationTest.java`
- [x] T015 [US2] Implement persisted candidate review and routes in `src/main/java/com/jobtrace/jobmarket/application/`, `infrastructure/` and `web/`

## Phase 5: User Story 3 — Observe existing ingestion (P3)

**Goal**: Inspect already-persisted source, candidate and sync outcomes without Java collection or lease claims.

**Independent Test**: Seed persisted run records, inspect them as an administrator and confirm no new run or lease is created.

- [x] T016 [US3] Test persisted run history, admin isolation and no lease writes in `src/test/java/com/jobtrace/jobmarket/JobMarketSyncAdminIntegrationTest.java`
- [x] T017 [US3] Keep only read-side sync history contracts/query in `src/main/java/com/jobtrace/jobmarket/application/SyncRunHistoryQuery.java` and `infrastructure/PostgresSyncRunHistoryQuery.java`
- [x] T018 [US3] Verify Java rejects duplicate sync without writing a run in `src/test/java/com/jobtrace/jobmarket/JobMarketSyncAdminIntegrationTest.java`

## Phase 6: Polish

- [x] T019 Document shared-database ownership in `docs/migration-slices/job-market-operations.md` and `docs/migration.md`
- [x] T020 Run `./mvnw verify`, inspect changed-code coverage and record representative timings in `specs/027-job-market-operations/quickstart.md` and `src/test/java/com/jobtrace/jobmarket/JobMarketCatalogPerformanceTest.java`

## Dependencies and execution

T001–T002 precede foundation. T003 precedes US1; T004 precedes US2 and US3. US1 is the minimum viable increment; US2 and US3 remain independently testable. Tests precede corresponding production code. No sub-agents, Java crawling, frontend work, deployment or CI/CD changes are in scope.
