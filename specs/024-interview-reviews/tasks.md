# Tasks: Interview Reviews and Public Sharing

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/openapi.yaml](contracts/openapi.yaml)

## Phase 1: Setup

- [x] T001 Record legacy interview routes and behavior in `specs/024-interview-reviews/research.md` and `specs/024-interview-reviews/contracts/openapi.yaml`.

## Phase 2: Foundation

- [x] T002 Define owner boundaries, safe public projection and rollback in `specs/024-interview-reviews/data-model.md` and `specs/024-interview-reviews/plan.md`.

## Phase 3: User Story 1 — Private review commands (P1)

**Independent Test**: Create, update and delete an owned review; reject stale and foreign writes.

- [x] T003 [US1] Add private command validation and HTTP tests in `src/test/java/com/jobtrace/interviews/InterviewCommandRequestTest.java` and `InterviewCommandIntegrationTest.java`.
- [x] T004 [US1] Implement validated create/edit payloads in `src/main/java/com/jobtrace/interviews/web/InterviewCommandRequest.java`.
- [x] T005 [US1] Implement private command use cases and JDBC owner-aware operations in `src/main/java/com/jobtrace/interviews/application/InterviewCommands.java` and `infrastructure/PostgresInterviewCommandStore.java`.
- [x] T006 [US1] Expose create/edit/delete routes and error mappings in `src/main/java/com/jobtrace/interviews/web/InterviewCommandController.java` and `InterviewCommandExceptionHandler.java`.

## Phase 4: User Story 2 — Public feed and detail (P2)

**Independent Test**: Publish, browse and unpublish a review; verify anonymity and no private fields.

- [x] T007 [US2] Add public read/privacy tests in `src/test/java/com/jobtrace/interviews/PublicInterviewReadIntegrationTest.java`.
- [x] T008 [US2] Implement safe public models and list parameter validation in `src/main/java/com/jobtrace/interviews/domain/PublicInterviewPage.java` and `web/PublicInterviewListParameters.java`.
- [x] T009 [US2] Implement owner-independent public availability queries, cursor pages and view recording in `src/main/java/com/jobtrace/interviews/infrastructure/PostgresPublicInterviewQuery.java`.
- [x] T010 [US2] Expose public list/detail routes in `src/main/java/com/jobtrace/interviews/web/PublicInterviewController.java`.

## Phase 5: User Story 3 — Public engagement (P3)

**Independent Test**: Like/unlike and comment on a public review, then unpublish and reject interactions.

- [x] T011 [US3] Add engagement validation, counter and availability tests in `src/test/java/com/jobtrace/interviews/PublicInterviewEngagementTest.java`.
- [x] T012 [US3] Implement function-backed like/comment operations in `src/main/java/com/jobtrace/interviews/infrastructure/PostgresPublicInterviewEngagementStore.java`.
- [x] T013 [US3] Expose like and comment routes in `src/main/java/com/jobtrace/interviews/web/PublicInterviewEngagementController.java`.

## Final Phase: Verification

- [x] T014 Run `./mvnw verify`, inspect changed-code coverage and record local p95 evidence in `specs/024-interview-reviews/quickstart.md`.
- [x] T015 Document route ownership and rollback in `docs/migration-slices/interview-review-operations.md`.

## Dependencies and Strategy

T001–T002 are foundational and complete. T003–T006 deliver the private MVP. T007–T010 deliver public browsing, then T011–T013 add interaction. T014–T015 close the stage. No subagents or CI/CD work. All tasks use the required checkbox/ID/story/file-path format; user stories are independently testable after foundation.
