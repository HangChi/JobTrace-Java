# Feature Specification: Administration Read Model

**Feature Branch**: `codex/009-admin-read-model`

**Created**: 2026-10-03

**Status**: Complete

**Input**: User description: "开始009阶段". Under the established migration roadmap, feature 009 starts the administration domain with a bounded read-only slice. Administrative mutations, sessions, schema ownership, deployment, and production traffic remain with the current service.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Review Operational Summary (Priority: P1)

As an administrator, I can review account and activity totals so that I can understand current platform usage even when one summary source is temporarily unavailable.

**Why this priority**: The summary is the entry point to the administration console and provides immediate operational value without taking write ownership.

**Independent Test**: Seed users, sessions, applications, and interviews across the 7- and 30-day windows; verify totals, Shanghai calendar boundaries, a complete 30-day trend, administrator-only access, and independent unavailable states.

**Acceptance Scenarios**:

1. **Given** representative platform data, **When** an administrator requests the summary, **Then** totals, activity windows, and the daily trend match the existing service.
2. **Given** one summary source fails, **When** the summary is requested, **Then** that section is unavailable while the independent section remains usable.
3. **Given** a normal or disabled user, **When** the summary is requested, **Then** no administrative data is disclosed.

---

### User Story 2 - Find and Inspect Managed Users (Priority: P2)

As an administrator, I can search, filter, page, and inspect managed users and their recent content so that I can investigate an account before deciding whether the legacy service should change its access.

**Why this priority**: Safe investigation is required before any future administrative write migration and exercises sensitive cross-owner reads under explicit administrator authorization.

**Independent Test**: Seed multiple roles, account states, registration dates, sessions, applications, interviews, stages, questions, actions, and audits; verify filtering, stable ordering, clamped pages, full detail composition, and safe not-found behavior.

**Acceptance Scenarios**:

1. **Given** mixed accounts, **When** an administrator applies supported filters, **Then** only matching users appear with exact totals and stable newest-first ordering.
2. **Given** a managed user, **When** an administrator opens the detail, **Then** the account summary, ten recent audit events, and independently paged application and interview content match the existing service.
3. **Given** an unknown or overlong identifier, **When** detail is requested, **Then** a safe validation or not-found response is returned without disclosing unrelated data.

---

### User Story 3 - Review Administrative Audit Events (Priority: P3)

As an administrator, I can search and filter append-only access audit events so that I can reconstruct sensitive account-management decisions.

**Why this priority**: Audit visibility completes the read-only administration journey while leaving access changes with the established writer.

**Independent Test**: Seed success, denial, conflict, failure, deleted-actor, and deleted-target events; verify every filter, inclusive Shanghai dates, immutable snapshots, stable ordering, pagination clamping, and administrator-only access.

**Acceptance Scenarios**:

1. **Given** mixed audit history, **When** an administrator filters by actor, target, event, outcome, or date, **Then** results and totals match the existing service.
2. **Given** an actor or target has been deleted, **When** its event is viewed, **Then** the stored identifier snapshot remains visible and deletion is explicitly indicated.

### Edge Cases

- Empty and out-of-range pages return the established page metadata; non-empty out-of-range user and audit pages clamp to the last page.
- Blank optional text is omitted; text longer than 100 characters, malformed dates, reversed ranges, unsupported enums, and invalid page sizes are rejected.
- Summary counts and activity fail independently and never return partial values inside one section.
- Null recovery email falls back to the internal email; null display names fall back to username and then the internal email prefix.
- The 30-day trend includes zero-value days and uses Asia/Shanghai calendar boundaries.
- Missing linked stage data retains the interview stage snapshot.
- Administrative responses are private and not cacheable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The slice MUST expose only the established administration summary, managed-user list/detail, and audit-event read behavior.
- **FR-002**: Every operation MUST require a current trusted administrator assertion; ordinary, disabled, absent, forged, expired, replayed, path-mismatched, and method-mismatched identities MUST fail closed.
- **FR-003**: The summary MUST preserve total user, active user, disabled user, active administrator, application, and interview counts.
- **FR-004**: Activity MUST preserve registered and active-user counts for inclusive 7- and 30-day Shanghai calendar windows and a gap-free 30-day daily trend.
- **FR-005**: Summary count and activity sections MUST degrade independently to an unavailable state.
- **FR-006**: The user directory MUST support trimmed text, role, status, inclusive registration dates, page, and limit filters with the established defaults and bounds.
- **FR-007**: User-directory text matching MUST cover username, internal email, and recovery email without exposing any other private content.
- **FR-008**: User pages MUST use stable created-time and identifier descending ordering, exact totals, and last-page clamping for non-empty out-of-range requests.
- **FR-009**: User summaries MUST preserve display-email fallback, username fallback, role, disabled state, access version, creation and last-sign-in times, and application/interview counts.
- **FR-010**: User detail MUST include the ten newest target audit events and independently paged application and interview content with established child ordering and snapshots.
- **FR-011**: User identifiers MUST contain 1 through 128 characters; missing users MUST return a non-disclosing not-found response.
- **FR-012**: Audit listing MUST support trimmed actor and target text or exact identifiers, event type, outcome, inclusive occurrence dates, page, and limit filters.
- **FR-013**: Audit pages MUST use stable creation-time and identifier descending ordering, exact totals, and last-page clamping for non-empty out-of-range requests.
- **FR-014**: Audit results MUST preserve immutable actor/target identifier snapshots, deletion flags, reason, before/after access state, failure code, request identifier, and timestamp.
- **FR-015**: All successful responses and administration errors MUST prevent shared or browser caching.
- **FR-016**: Diagnostics MUST use bounded operation, outcome, and latency categories and MUST NOT contain account identifiers, emails, search text, reasons, request identifiers, audit payloads, or returned content.
- **FR-017**: Reads MUST issue parameterized queries, remain bounded and set-based, and MUST NOT create, update, delete, lock, or invoke access-change routines.
- **FR-018**: The existing service MUST remain the sole administration writer, identity/session authority, schema owner, frontend owner, deployment owner, and production traffic owner.
- **FR-019**: Account access mutation, session management, job-market administration, synchronization, frontend changes, deployment, and traffic routing are outside this slice.
- **FR-020**: Automated tests MUST cover contract parity, authorization, input bounds, sensitive-data handling, deterministic ordering, partial summary failure, read-only behavior, and representative performance.

### Key Entities

- **Administrative Summary**: Independently available platform totals and recent account-activity windows/trend.
- **Managed User**: An account access projection plus aggregate activity counts and recent sign-in time.
- **Managed User Detail**: A managed user with recent audit history and bounded application/interview pages.
- **Administrative Audit Event**: An immutable access-decision record whose identity snapshots survive account deletion.
- **Page Query**: Validated filters and deterministic page metadata; it never selects authorization or write ownership.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Representative summary, user, detail, and audit fixtures achieve 100% asserted field, ordering, filtering, paging, and error parity with the current service.
- **SC-002**: Across all authorization tests, zero administrative fields are returned to a non-administrator or invalid assertion.
- **SC-003**: Every successful list or detail request performs a bounded number of reads independent of returned child counts and completes within 500 ms p95 on the documented representative data set.
- **SC-004**: Thirty consecutive Shanghai calendar days are always present in the activity trend, including zero-value days.
- **SC-005**: Verification observes zero database or schema mutations and zero access-change function calls from this slice.
- **SC-006**: Changed production code maintains at least 80% line and branch coverage, with all local quality gates passing.

## Assumptions

- Better Auth and the existing service remain authoritative for sessions, roles, disabled state, access-version changes, and signed assertion issuance.
- Existing PostgreSQL tables and stored audit snapshots remain unchanged and are available to the Java runtime as read-only data.
- Administrator user IDs are opaque text up to 128 characters; no UUID-only assumption is introduced.
- Asia/Shanghai remains the administration reporting calendar zone.
- The existing browser administration experience and terminology remain unchanged.
