# Research: Private Interview Read Model Migration

## Evidence baseline

The existing service defines private reads in `src/app/api/interviews/route.ts`, `src/app/api/interviews/[id]/route.ts`, and `src/app/api/applications/[id]/detail/route.ts`. The request parser and response types live in `src/modules/interviews/application/list-query.ts` and `contracts.ts`; the owner-scoped SQL and mapping live in `src/modules/interviews/infrastructure/postgres-interview-repository.ts`. The schema originates in `supabase/migrations/20260818000200_interview_reviews.sql`, with later assessment, publication, and autosave revisions. Feature 004 already implements the core application detail read in Java.

## Slice boundary

**Decision**: Implement only the three private GET operations: review list, review detail, and application dialog data with interview summaries. Exclude public feed/detail, view counting, likes, comments, export, and all mutations.

**Rationale**: The existing private routes are owner-scoped and side-effect-free. Public detail records a view and therefore is not read-only. The dialog aggregation was explicitly deferred by feature 004 and is naturally completed with the private interview read model.

**Alternatives considered**: Migrating all interview routes would transfer publication and engagement writes; migrating only list/detail would leave the feature 004 dialog gap.

## Private query contract

**Decision**: Preserve the existing parser: trimmed `q` with maximum 200 characters; optional UUID `applicationId`; repeated `status`, `stage`, `result`, and `publication` enum filters; optional strict ISO dates; `limit` default 50 and valid range 1–100. Empty repeated values are ignored, but unknown enum values, invalid dates, invalid UUIDs, and out-of-range limits produce validation failures. Filter combinations use AND between groups and OR within a repeated group.

**Rationale**: Unlike the tolerant application list, the interview list's Zod parser rejects invalid optional values. Copying feature 004 normalization would silently change client behavior.

**Alternatives considered**: Tolerant filtering and limit clamping were rejected because they do not match the current interview route.

## Cursor and ordering

**Decision**: Keep descending `(interviewed_on, id)` order and the existing base64url JSON cursor with nonempty `value` date and UUID `id`. Decode strictly before SQL. A cursor is not an authorization token or a filter fingerprint; reuse under different filters follows the current tuple-navigation behavior, while the owner predicate still applies. `total` counts all filtered owner rows before cursor navigation.

**Rationale**: Existing clients may retain cursors. The UUID tie-breaker gives deterministic traversal when reviews share a date.

**Alternatives considered**: A new signed or filter-bound cursor was deferred as a compatibility change. Offset paging was rejected because the existing response exposes only cursor navigation.

## Existing database read model

**Decision**: Use parameterized Spring JDBC against the unchanged `interview_reviews`, `interview_questions`, `interview_action_items`, `applications`, and optional `application_stage_occurrences` tables. Read the display stage from the linked occurrence when present, otherwise `stage_snapshot`. Include both private and published reviews in the owner's private list. Preserve question and action-item `sort_order` and date/ID ordering for application summaries.

**Rationale**: Explicit SQL matches the existing PostgreSQL queries, repository policy, and feature 004 approach. The schema remains owned by the existing service; Flyway stays disabled.

**Alternatives considered**: JPA was rejected by repository guidance. A new denormalized table or migration is unnecessary and would violate the no-schema-ownership boundary.

## Cross-context application dialog

**Decision**: Compose the existing Java `GetApplicationDetail` use case with a new owner-scoped `ListInterviewsForApplication` use case through a thin dialog orchestrator. Confirm the application is owned before returning any interview data. Keep `applications` and `interviews` domain packages independent; add an explicit architecture rule for the orchestration edge and no cycles.

**Rationale**: The existing route returns `{ application, interviews }`. Reusing feature 004 prevents a second implementation of application detail and makes the cross-context boundary visible.

**Alternatives considered**: Duplicating application SQL inside interviews and making either domain package depend on the other were rejected as unnecessary coupling.

## Authentication, errors, and diagnostics

**Decision**: Extend the disabled-by-default feature 003 bridge allowlist to the three exact GET route patterns. Require the `BridgeIdentity` principal in each controller, including when ordinary Spring authentication is present; never accept owner headers. Return `private, no-store`, request IDs, and safe problem responses. Contract tests compare success JSON structure and the legacy error status/code semantics; the Java repository's established `application/problem+json` envelope remains its wire-format convention. Metrics use bounded operation/outcome labels only.

**Rationale**: A route protected only by generic Spring authentication would bypass the reviewed server-to-server identity contract. Legacy and Java error envelopes are not byte-identical, so parity assertions must state exactly what is compared.

**Alternatives considered**: Issuing new session tokens in Java, trusting headers, and full error-body byte equality were rejected as inconsistent with the current migration boundary.

## Verification without deployment

**Decision**: Check in synthetic legacy fixtures for empty/filtered/paged private lists, owned review detail (including unlinked stages), and application dialog data. Use Testcontainers PostgreSQL with a minimal test-only schema aligned to the existing migration head. Test owner isolation at root and child-query boundaries, cursor traversal, invalid parameters, signed-assertion denial, missing/cross-owner equivalence, and dependency failure. Measure 500 ms p95 at a documented representative load. Run the existing Maven verification and artifact CI gates; do not activate Java traffic.

**Rationale**: The user does not plan to deploy Java. Deterministic local/CI evidence is the appropriate completion criterion for this slice.

**Alternatives considered**: A live cross-service or production canary test was rejected because it requires a deployment decision outside 005; unit tests alone cannot prove SQL and JSON parity.
