# Validation report: application read model

Validation date: 2026-10-02

## Scope and dependency

Feature 004 implements only `GET /api/applications` and
`GET /api/applications/{id}` in the Java repository. The implementation is
stacked on feature 003's signed identity bridge. The existing JobTrace service
remains the sole writer, session owner, schema owner, and production traffic
owner. No Java deployment or traffic activation is planned for this feature.

## Local verification

- `./mvnw verify`: PASS with Docker and Testcontainers PostgreSQL.
- Tests: 112 run, 0 failures, 0 errors, 1 existing skipped packaging test.
- JaCoCo bundle: 96.78% line and 83.72% branch coverage.
- Applications package: 98.35% line and 86.47% branch coverage.
- ArchUnit: PASS, including the applications domain, use-case, and adapter
  dependency rules.
- Checkstyle: 0 violations.
- Repository and feature OpenAPI parsing: PASS.
- Embedded React/Vite frontend build and Spring Boot repackaging: PASS.
- The produced Spring Boot JAR includes the compiled application module and
  `BOOT-INF/classes/static/index.html`.

The 100 additional application row performance fixture, fixed clock, warm-up,
and 40 measured list/detail samples met the 500 ms p95 budget in the local
Testcontainers run. This is representative lab evidence, not production
latency evidence.

## Contract and safety evidence

The synthetic legacy fixtures match the Java empty page, representative page,
and core detail responses with array ordering preserved. PostgreSQL tests
cover combined search and filters, inclusive date boundaries, default
status-priority order, offset pages, and cursor traversal for four sorts in
both directions without duplicate or missing rows. List counts and detail
history stay owner-scoped.

The two routes reject public owner headers and ordinary authenticated
principals lacking the bridge identity. Signed assertion tests cover valid
ownership, missing assertions, invalid signatures, expired assertions,
path mismatch, and replay on both routes. Missing and cross-owner detail
identifiers return the same 404 status, code, and detail. A database failure
returns a non-disclosing 503 problem. The controller exposes only two GET
handlers. The legacy schema marker is unchanged, Java has no production
migration, and Flyway remains disabled.

Application read metrics distinguish success, invalid input, identity denial,
not-found, dependency failure, and latency-budget breach with only bounded
operation and outcome labels. Request identifiers are supplied through the
existing request filter. No owner, application ID, query, token, or response
body is used as a metric label.

## Solo-Maintainer Mode self-review

- **Specification**: FR-001–FR-020 and all three user stories were compared
  against the diff. A default-disabled bridge initially allowed a normal
  Spring Security principal into the new controller; the controller now
  requires `BridgeIdentity`, and a regression test expects 401 for an
  ordinary principal. The interview-enriched dialog and mutations are absent.
- **I. Maintainable Code by Design**: Domain normalization, application
  use cases, parameterized PostgreSQL adapter, and HTTP adapter follow the
  documented dependency direction. SQL sort expressions come only from fixed
  enum cases. The module adds no new persistence framework or deployment unit.
- **II. Testing Is a Release Gate**: Contract, query, cursor, controller,
  signed identity, owner isolation, dependency outage, read-only surface,
  performance, and architecture tests pass. Both package-specific coverage
  floors exceed 80%. The one skipped packaging test predates 004 and is
  covered by the repository's production-artifact CI check.
- **III. Consistent and Accessible User Experience**: No browser flow or
  frontend source changes. JSON fields, empty state, ordering, defaults,
  error shape, and `private, no-store` response headers are exercised in
  tests. There is no new interaction requiring separate accessibility review.
- **IV. Measured Performance Budgets**: A reproducible 102-row owner fixture
  checks list and detail p95 at 500 ms. The code records bounded latency
  histograms and budget-breach counts for later diagnostics.

No exception to the constitution is requested. Production deployment,
canary observation, and rollback rehearsal are deliberately outside 004 and
will require a separate release decision if Java is ever activated.

## Pull request and CI

- Stacked Java pull request: [#16](https://github.com/HangChi/JobTrace-Java/pull/16),
  based on `codex/003-signed-identity-bridge` (PR #15).
- Backend, frontend, production-artifact, dependency-review, and secret-scan
  checks passed on implementation commit `b618a8c`.
- There were no open review conversations when the self-review was recorded.
- Java deployment and production traffic activation remain unscheduled.

Feature 003 must merge before this PR is retargeted to `main`; retargeting
requires a fresh diff and CI check. No merge or release action is part of
feature 004 verification.
