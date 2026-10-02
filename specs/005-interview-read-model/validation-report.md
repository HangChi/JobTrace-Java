# Feature 005 validation report

## Scope

The private interview list, owned review detail, and application dialog GET operations are implemented. The existing service still owns sessions, all writes, publication/engagement, schema migrations, and production traffic. Java deployment and cutover are unscheduled.

## Independent user-story evidence

| Story | Evidence | Local result |
| --- | --- | --- |
| US1 private list | Synthetic empty/representative/filtered/cursor JSON parity; strict HTTP inputs; PostgreSQL owner isolation, question search, publication modes, inclusive filters, same-date UUID cursor traversal | Passed in focused Maven tests |
| US2 private detail | Complete/empty/unlinked JSON parity; saved child order; owner-bound root/child SQL; missing and cross-owner 404 status/code parity | Passed in focused Maven tests |
| US3 application dialog | Reuses the feature 004 application detail unchanged; synthetic with/without-interviews JSON parity; application ownership checked before interview query; owner-bound summaries and uniform 404 | Passed in focused Maven tests |

All fixtures and database rows are synthetic. The Testcontainers schema is under `src/test/resources/postgres/`; no production migration was added.

## Security, failure, and performance evidence

- Three private GET routes reject missing, forged, expired, path-mismatched, and replayed assertions. Public owner headers and ordinary principals do not authorize them. Bridge enablement remains opt-in.
- Storage outages return the project's safe `storage_unavailable` problem and request ID without SQL or private content. Metrics use only bounded operation/outcome labels.
- Representative PostgreSQL 17 load: 103 owned reviews, 10 warm-ups, 40 measured samples per operation. The measured p95 values were below 1 ms for list, detail, and dialog on this local run (the test reports integer milliseconds as `0/0/0`). Fixed query counts were list 2, detail 3, dialog 4. The test asserts each p95 at or below 500 ms.

## Local verification (2026-10-02)

- `./mvnw verify -q` passed with Docker and PostgreSQL 17 Testcontainers: 153 tests, 0 failures, 0 errors, 1 skipped. This includes legacy JSON contract parity, owner isolation, bridge security, OpenAPI baseline, architecture, migration safety, read-only surface, and dependency-failure checks.
- The configured Maven Checkstyle goal passed. JaCoCo overall line/branch coverage was 97.01%/84.05%; coverage for the new interview/dialog code and modified bridge classes was 97.83%/84.81%, exceeding the 80% changed-code floor on both axes.
- The Maven verify lifecycle built the React/TypeScript frontend and the Spring Boot artifact, including the existing Java-only production artifact check. `target/jobtrace-0.1.0-SNAPSHOT.jar` was produced. No Gradle build or production schema migration is involved.
- `git diff --check` passed. Fixtures, identities, and database rows are synthetic.

## Solo-maintainer self-review

- Specification: FR-001–FR-018 and SC-001–SC-006 are covered by the three independent story suites and cross-cutting security, parity, performance, and schema-safety tests. The only new HTTP surface is the three private GET routes. No production deployment or traffic activation is part of this feature.
- Principle I, maintainability: the bridge extracts a trusted owner once; interview domain types, use cases, JDBC queries, web handling, and the thin application-dialog composition remain separated. The dialog reuses feature 004 application detail rather than duplicating its SQL. The configured static checks pass.
- Principle II, testing: unit, HTTP, contract, PostgreSQL integration, architecture, and performance tests pass; changed production code exceeds 80% line and branch coverage. The one skipped test is pre-existing and is not needed for feature 005 acceptance.
- Principle III, consistent and accessible UX: no browser UI or interaction was changed. The API preserves established JSON fields, empty/null forms, collection order, validation and not-found behavior; private responses are no-store.
- Principle IV, measured performance: fixed synthetic representative load, 10 warm-ups and 40 samples per operation produced p95 below the 500 ms read budget with constant query counts 2/3/4. This is local evidence, not a production latency claim.

## Pull request and release boundary

PR [#19](https://github.com/HangChi/JobTrace-Java/pull/19) targets `main`. At commit `fe4582e`, required `backend`, `frontend`, `production-artifact`, `dependency-review`, and `secret-scan` checks all passed. GitHub reported `CLEAN` merge state; there were no review comments or conversations to resolve. The sole maintainer's written self-review is above. This record-only update will rerun CI on its own commit before merge. Deployment remains unscheduled.
