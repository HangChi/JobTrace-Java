# Feature Specification: Account Session Read Model

**Feature Branch**: `codex/011-session-read-model`

**Created**: 2026-10-03

**Status**: Complete

**Input**: User description: "开始011阶段". Under the established migration roadmap, feature 011 starts the identity-and-access domain with the bounded account-session read path. Authentication, session issuance and revocation, profile changes, schema ownership, deployment, routing, and CI/CD remain with the existing service.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Review Active Login Sessions (Priority: P1)

As a signed-in account owner, I can review my active login sessions so that I can recognize the devices that currently have access to my account.

**Why this priority**: Session visibility is the smallest independently useful identity-and-access slice and does not transfer authentication or mutation ownership.

**Independent Test**: Seed active, expired, and foreign sessions; request the list as one owner with the current session identified; verify exact fields, current-device marking, newest-first ordering, owner isolation, authorization, and no database changes.

**Acceptance Scenarios**:

1. **Given** an owner with several active sessions, **When** the owner requests login sessions, **Then** every active owned session is returned newest first with identifier, creation time, expiry time, device agent, and exactly the matching current-device marker.
2. **Given** expired sessions or sessions belonging to another account, **When** the owner requests login sessions, **Then** those sessions are not disclosed.
3. **Given** an absent, invalid, replayed, or request-mismatched trusted identity, **When** sessions are requested, **Then** no session metadata is returned.

### Edge Cases

- An account with no active sessions receives an empty list.
- A null device agent remains null so the existing client can show its established unknown-device label.
- Equal creation times use the session identifier as a deterministic descending tie-breaker.
- If the supplied current-session identifier is not among the owner's active sessions, every returned item is marked non-current.
- Blank or identifiers longer than 255 characters are rejected without querying by that value.
- Responses are private and not cacheable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The slice MUST expose only the established active account-session list behavior.
- **FR-002**: The operation MUST require a current trusted owner assertion; absent, forged, expired, replayed, method-mismatched, and path-mismatched assertions MUST fail closed.
- **FR-003**: The caller MUST identify the current session using trusted server-to-server request metadata containing 1 through 255 characters.
- **FR-004**: Results MUST include only unexpired sessions owned by the asserted account.
- **FR-005**: Each result MUST preserve identifier, creation time, expiry time, nullable device agent, and current-device status.
- **FR-006**: Results MUST be ordered by creation time descending and session identifier descending.
- **FR-007**: At most the active owned session matching the supplied current-session identifier MUST be marked current; an unmatched identifier MUST not disclose whether it exists for another owner.
- **FR-008**: Successful responses and errors from this surface MUST prevent shared or browser caching.
- **FR-009**: Diagnostics MUST use bounded operation, outcome, and latency categories and MUST NOT contain account identifiers, session identifiers, device agents, assertion contents, or returned session data.
- **FR-010**: Reads MUST be parameterized and MUST NOT create, update, delete, lock, refresh, or revoke sessions.
- **FR-011**: Better Auth and the existing service MUST remain the sole authority for authentication, session issuance, refresh, expiry, revocation, cookies, profile changes, credentials, and schema changes.
- **FR-012**: Session revocation, login, logout, registration, profile and credential changes, frontend changes, deployment, routing, production traffic, and CI/CD are outside this slice.
- **FR-013**: Automated tests MUST cover contract parity, owner isolation, expiry filtering, current marking, deterministic ordering, authorization, identifier bounds, read-only behavior, dependency failures, diagnostics, and representative performance.

### Key Entities

- **Account Session**: A currently unexpired login session owned by one account, including its opaque identifier, timestamps, nullable device agent, and derived current-device marker.
- **Current Session Reference**: Trusted request metadata used only to mark one returned owned active session; it does not grant ownership or select another account.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Representative fixtures achieve 100% asserted field, filtering, ordering, and current-device parity with the existing service.
- **SC-002**: Across authorization and isolation tests, zero session fields belonging to another account are returned.
- **SC-003**: The session list completes within 500 ms p95 with 500 representative active sessions.
- **SC-004**: Verification observes zero inserts, updates, deletes, locks, or session-management calls from this slice.
- **SC-005**: Changed production code maintains at least 80% line and branch coverage, with all local quality gates passing.

## Assumptions

- The trusted legacy caller can forward the authenticated Better Auth session identifier as server-to-server metadata when this route is eventually activated.
- Existing session identifiers are opaque text no longer than 255 characters.
- Existing PostgreSQL session rows and UTC timestamps remain available without schema changes.
- Java deployment and traffic activation are not part of this phase.
