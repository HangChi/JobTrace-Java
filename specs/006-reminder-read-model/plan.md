# Implementation Plan: Private Reminder Read Model Migration

**Branch**: `codex/006-reminder-read-model` | **Date**: 2026-10-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/006-reminder-read-model/spec.md`

## Summary

Add two owner-scoped, read-only compatibility operations for the existing scheduled-reminder overview and reminder preferences. Preserve active/history grouping, 200-item ordering, current-schedule email-attempt state, verified-email availability, and preference defaults. Reuse the reviewed signed identity bridge and safe-problem patterns, query the unchanged legacy PostgreSQL schema with explicit parameterized SQL, and prove parity with synthetic fixtures and isolated tests. Do not transfer reminder writes, email or delivery scheduling, schema ownership, browser routing, deployment, or traffic.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Existing Spring Boot 4.1.1 Web MVC, Spring Security, Spring JDBC, Jackson, and Micrometer; no new production dependency

**Storage**: Existing PostgreSQL 17 `scheduled_reminders`, `reminder_notification_attempts`, `applications`, and `users`; read-only access with Flyway disabled

**Testing**: JUnit 5, Spring Boot Test, MockMvc, Spring Security Test, Testcontainers PostgreSQL, ArchUnit, OpenAPI parser, JaCoCo, synthetic legacy fixtures, and existing artifact CI

**Target Platform**: Java 21 Linux runtime; local and CI verification only, no Java deployment in feature 006

**Project Type**: Spring Boot modular monolith with unchanged embedded React/TypeScript frontend artifact

**Performance Goals**: Reminder overview and preferences each ≤ 500 ms p95 at a documented representative load and fixed query-count ceiling

**Constraints**: Exact legacy read contract; only trusted signed principal; owner predicates across joins and current-schedule attempts; safe no-store responses; no schema mutation, reminder write, delivery, email integration, frontend change, or cutover

**Scale/Scope**: Two protected GET operations, at most 200 returned reminders, three selection modes, four reminder statuses, four email-attempt states, and five preference values

## Constitution Check

*GATE: Passed before research and rechecked after design.*

- **I. Maintainable Code by Design**: One `reminders` read context with immutable projections, a small query port and explicit JDBC adapter. Reuse the existing identity boundary, global safe-error handling, and metrics conventions. No new service, production dependency, or cross-context package cycle.
- **II. Testing Is a Release Gate**: Unit tests for selection, time boundaries, defaults, grouping and use cases; PostgreSQL tests for all owner-bound joins, cap, current-schedule attempt and email privacy; HTTP/contract tests for both routes and signed identity; read-only, architecture, performance, and artifact gates. Changed production code must meet ≥ 80% line and branch coverage.
- **III. Consistent and Accessible User Experience**: No browser source or navigation change. Match current reminder and settings JSON, status wording, empty arrays, nulls, time format and safe failures. Existing frontend accessibility checks remain in CI.
- **IV. Measured Performance Budgets**: Each read has a 500 ms p95 budget under reproducible PostgreSQL data and a fixed clock, after warm-up with at least 40 samples. Query-count bounds detect avoidable per-reminder attempt lookups.
- **Delivery and review**: Solo-maintainer written self-review, PR, all required CI/security checks and resolved conversations remain mandatory before merge. This is code-and-CI only; production activation would require a separate release decision and rollback path.

Post-design recheck: passed. The design preserves the single-writer and no-deployment decisions and requires no constitutional exception.

## Security and Data Boundaries

1. Extend the feature 003 bridge matcher only for exact `GET /api/reminders` and `GET /api/reminder-settings` paths; use the same request-bound, one-time assertion and trusted-owner extractor as features 004–005. The bridge remains opt-in.
2. Bind the verified owner in reminder, application, user-profile and attempt reads. No public owner header, ordinary authenticated principal, reminder ID, or query parameter may select an owner. An inconsistent cross-owner application association returns no foreign display data.
3. The overview accepts only the established selection: `active`, `completed`, or `cancelled`; unknown/absent values default to active as the current route does. It has no pagination or total.
4. Capture one clock instant per request. Use it for the due/elapsed sort and due/upcoming grouping so exact-boundary results cannot disagree within a response; no read advances stored reminder status or delivery attempts.
5. Show an attempt state only for the current notification time, email channel, and owner. Return a recovery address only when the owner's current verified email is available. No secret or private content enters logs or metric labels.
6. Return `private, no-store` on success and project-safe problems with request IDs on failures. Keep all writes, delivery jobs, retries and email transport in the existing service.

## Project Structure

### Documentation (this feature)

```text
specs/006-reminder-read-model/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/openapi.yaml
├── checklists/requirements.md
└── tasks.md
```

### Source Code (future implementation)

```text
src/main/java/com/jobtrace/reminders/
├── domain/             # immutable reminder, summary, email, preference, selection types
├── application/        # two owner-scoped read use cases and query port
├── infrastructure/     # parameterized PostgreSQL adapter and row mapping
└── web/                # two private GETs, bounded metrics

src/main/java/com/jobtrace/identityaccess/web/BridgeAuthenticationFilter.java
src/test/java/com/jobtrace/reminders/
src/test/resources/contracts/reminders/
src/test/resources/postgres/
```

**Structure Decision**: Keep scheduled-reminder reads in one new context. They may reference the `applications` table for owner-consistent display names but not depend on the application module's domain or mutate its state. Keep the existing React/TypeScript frontend untouched.

## Verification Stages

1. **Legacy baseline**: Record current route, repository mapper, preferences, exact status fallback, 200-item cap, ordering, time serialization and source OpenAPI drift. Capture synthetic active, history, email and preference fixtures.
2. **Foundational domain**: Add immutable response models, selection normalization, fixed-clock due classification, read query port, and trusted-owner route tests before SQL or controllers.
3. **Storage parity**: Add a test-only PostgreSQL schema and two-owner rows aligned to the legacy migration head. Verify owner predicates on reminders, joined application, attempts, email and preferences; test 200-item cap, same-time ID ordering, old-schedule attempt exclusion, and no state change.
4. **Protected HTTP**: Add the two exact GET routes behind the signed bridge; verify absent, forged, expired, path-mismatched, replayed and ordinary-principal denial, no-store headers, safe dependency problems and bounded metrics.
5. **Convergence**: Parse the feature and repository OpenAPI contracts, compare synthetic legacy JSON, measure p95 and query bounds, run architecture/coverage/static/artifact checks and `./mvnw verify`, then record self-review and PR CI evidence. No deployment or routing change.

## Key Risks and Mitigations

- **Stale legacy OpenAPI**: Feature 008's older schema names `suggestions` while the runtime summary has `history`, and it omits reminder-settings. Use current routes and fixtures as oracle; document the drift in contract tests.
- **Time-boundary mismatch**: The existing SQL sorts with database `now()` while application grouping evaluates the clock later. Bind one captured instant in Java and test exact-boundary cases; this resolves nondeterminism without changing intended grouping.
- **Cross-owner join leakage**: The existing reminder query joins applications without a second owner predicate. Add one in Java and test inconsistent synthetic rows; never return foreign company or position.
- **Old email-attempt confusion**: A snoozed reminder can retain an attempt for a previous notification time. Match only current `notifyAt` and email channel, with an owner predicate.
- **Scope creep into delivery**: The same legacy domain contains jobs, email side effects, versioned writes and user settings updates. Enforce read-only surface/schema-safety tests and document sole-writer ownership.

## Complexity Tracking

No constitution violations require justification. The slice reuses the existing monolith, security bridge and test infrastructure without adding a deployment unit or production dependency.
