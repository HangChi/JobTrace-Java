# Feature Specification: Account Session Revocation

**Feature Branch**: `codex/012-session-revocation`

**Created**: 2026-10-03

**Status**: Complete

**Input**: User description: "提交一下011，然后开始012". Following the feature 011 account-session list, feature 012 migrates only the established owner-initiated session revocation command. Better Auth and the existing service retain authentication, session issuance, refresh, cookies, all other identity settings, schema ownership, frontend routing, deployment, and production traffic.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Revoke an Owned Login Session (Priority: P1)

As a signed-in account owner, I can revoke one of my login sessions so that an unrecognized or unused device immediately loses access.

**Why this priority**: Revocation completes the session-management journey started in feature 011 while keeping the mutation narrowly owner-scoped.

**Independent Test**: Seed current, secondary, expired, and foreign sessions; revoke one owned identifier and verify exactly that row is removed, the established response is returned, retries and foreign identifiers produce the established not-found response, and invalid identities cannot delete anything.

**Acceptance Scenarios**:

1. **Given** an owned session exists, **When** its owner revokes it, **Then** exactly that session is removed and the response confirms revocation.
2. **Given** the owner revokes the session backing the current browser, **When** the response completes, **Then** that durable session is removed so subsequent authentication through it cannot succeed.
3. **Given** a missing, already-revoked, or foreign session identifier, **When** revocation is requested, **Then** a non-disclosing not-found response is returned and no other session changes.
4. **Given** an absent, invalid, replayed, or request-mismatched trusted identity, **When** revocation is requested, **Then** no session is removed.

### Edge Cases

- Missing, null, blank, non-string, and identifiers longer than 255 characters are rejected before mutation.
- Session identifiers are opaque and case-sensitive.
- Concurrent attempts to revoke the same session produce one success and one not-found result, with one deletion.
- Expired owned sessions remain revocable while the row exists, matching the existing command behavior.
- Successful and failed responses are private and not cacheable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The slice MUST expose only the established account-session revocation behavior on the existing session endpoint.
- **FR-002**: The command MUST require a current trusted owner assertion bound to the exact DELETE method, path, and request identifier; invalid assertions MUST fail closed.
- **FR-003**: The request MUST contain exactly one string session identifier with 1 through 255 characters.
- **FR-004**: The command MUST delete a session only when both its identifier and owner match the verified account.
- **FR-005**: A successful command MUST remove exactly one session and return `{ "revoked": true }`.
- **FR-006**: Missing, already-revoked, and foreign sessions MUST share the same safe not-found response.
- **FR-007**: The command MUST preserve opaque, case-sensitive identifier semantics and MUST permit revocation of an expired owned row that still exists.
- **FR-008**: Concurrent revocations of the same session MUST result in exactly one successful deletion.
- **FR-009**: Successful responses and errors from the session surface MUST prevent shared or browser caching.
- **FR-010**: Diagnostics MUST use bounded operation, outcome, and latency categories and MUST NOT contain account identifiers, session identifiers, user agents, assertion contents, or request bodies.
- **FR-011**: The mutation MUST be one parameterized, owner-scoped durable operation and MUST NOT read or modify unrelated sessions.
- **FR-012**: Better Auth and the existing service MUST remain authoritative for authentication, cookies, session creation, refresh, expiry, login, logout, registration, profile changes, credentials, and schema changes.
- **FR-013**: Login, logout, registration, bulk revocation, profile and credential changes, frontend changes, deployment, routing, production traffic, and CI/CD are outside this slice.
- **FR-014**: Automated tests MUST cover response parity, ownership, input bounds, not-found privacy, current and expired session deletion, concurrency, authorization, read/write surface limits, dependency failures, safe diagnostics, and representative performance.

### Key Entities

- **Session Revocation Command**: The verified owner and one opaque session identifier requested for deletion.
- **Session Revocation Result**: A success confirmation returned only after one owned row is durably removed.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Representative success, validation, not-found, and authorization fixtures achieve 100% asserted response and durable-state parity with the existing service.
- **SC-002**: Across ownership and authorization tests, zero foreign or unrelated sessions are deleted or disclosed.
- **SC-003**: Across concurrent same-session requests, exactly one request succeeds and exactly one row is deleted.
- **SC-004**: Under a reproducible representative data set, 95% of revocations complete within 1 second.
- **SC-005**: Changed production code maintains at least 80% line and branch coverage, with all local quality gates passing.

## Assumptions

- Feature 011's session endpoint, owner bridge, non-cache filter, and synthetic session schema remain the foundation.
- The legacy caller issues a DELETE-bound signed assertion only after Better Auth authenticates the current browser session.
- Existing session identifiers remain opaque text no longer than 255 characters.
- Java deployment and traffic activation are not part of this phase.
