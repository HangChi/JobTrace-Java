# Feature 006 validation report

## Scope

This slice implements only private scheduled-reminder and preference reads. The existing service remains the sole session, reminder writer, preference writer, notification-attempt writer, email sender, scheduler, schema owner, and production traffic owner. Java deployment is not planned.

## Independent story checkpoints

| Story | Evidence | Status |
| --- | --- | --- |
| US1 active reminder overview | Synthetic active/empty/exact-boundary JSON parity; owner-bound reminder, application, attempt, and email reads; 200-item cap; same-time order; no stored status mutation; HTTP no-store and ordinary-principal denial | Passed focused Maven tests |
| US2 completed/cancelled history | Synthetic completed/cancelled/unknown-selection JSON parity; PostgreSQL owner/status isolation, 200-item cap and time/UUID ordering; no-store HTTP responses with empty active groups | Passed focused Maven tests |
| US3 reminder preferences | Synthetic custom/default JSON parity; owner-scoped PostgreSQL values and no mutation; trusted-owner HTTP denial and no-store success | Passed focused Maven tests |

The fixture data and PostgreSQL schema are synthetic and test-only. The current legacy service is the read-contract oracle; its older feature 008 OpenAPI summary is stale as recorded in `research.md`.

## Final local gates

`./mvnw verify -q` passed on Java 21 with Docker/PostgreSQL 17. Surefire recorded
190 tests, zero failures/errors, and one pre-existing skipped test. The run included
synthetic fixture JSON parity, two-owner PostgreSQL isolation, both OpenAPI parsers,
bridge assertions for absent, forged, expired, path-mismatched and replayed tokens,
storage-failure redaction, bounded metric labels, architecture, read-only surface,
Flyway-disabled schema safety, Checkstyle, frontend build, and Java artifact packaging.
The produced Spring Boot JAR contains the compiled frontend assets, with no Node
runtime required for the production artifact.

JaCoCo CSV for the new reminder packages: 165/167 lines (98.8%) and 67/79 branches
(84.8%). Including the modified bridge filter, changed production classes have
214/216 lines (99.1%) and 94/107 branches (87.9%). Both exceed the 80% floor.

The representative PostgreSQL test uses 250 extra owned reminders, a fixed clock,
10 warm-ups and 40 samples per operation. Measured p95 was 1 ms for the 200-item
summary and <1 ms for preferences, against the 500 ms budget. Fixed query counts
were 2 and 1 respectively. `git diff --check` passed. No frontend file or production
database migration changed.

## Solo-Maintainer Mode self-review

- **Specification**: Both private GET contracts cover active/due/upcoming, completed
  and cancelled history, current-schedule email attempt, verified-address availability,
  and five settings values. Unknown status falls back to active. Writes and delivery
  remain in the legacy service.
- **I — Maintainable code**: One cohesive reminder read context uses immutable
  projections, two small use cases, one parameterized JDBC adapter, and existing
  identity/error infrastructure. Architecture tests reject outward package cycles.
- **II — Testing**: Contract, domain, use-case, HTTP, signed-bridge, PostgreSQL,
  failure, architecture and read-only tests passed. Changed-code line/branch coverage
  exceeds 80%; no test was weakened or disabled for this feature.
- **III — Consistent and accessible UX**: No frontend source or route changes. JSON
  shapes, UTC millisecond timestamps, empty groups, defaults and no-store behavior
  match the current read service's runtime contract. Existing frontend checks passed.
- **IV — Performance**: Both documented p95 limits and fixed query budgets passed
  under reproducible synthetic load; no background delivery runs on either read.

The self-review found no constitution exception. PR creation, required remote CI and
security checks are the remaining merge gate. No deployment or cutover is in scope.
