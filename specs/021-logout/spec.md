# Feature Specification: Compatible Current-Session Logout

**Feature Branch**: `codex/021-logout`

**Created**: 2026-10-04

**Status**: Implemented locally

**Input**: Continue the Java identity migration after compatible login with the established current-session logout flow.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Sign Out of the Current Browser Session (Priority: P1)

As a signed-in user, I can sign out so the current browser session stops working without affecting sessions on my other devices.

**Why this priority**: Feature 020 can create sessions; users need a safe way to end one.

**Independent Test**: Sign in twice, sign out with one browser cookie, and confirm only the matching session is removed while both browser sign-out cookies and the other session behave as expected.

**Acceptance Scenarios**:

1. **Given** a valid current-session cookie, **when** same-origin logout is requested, **then** only that session is removed and the browser's authentication cookies are expired; the response is empty HTTP 204.
2. **Given** no cookie, a tampered cookie, or a cookie for an already-removed session, **when** logout is requested, **then** the response remains HTTP 204 and authentication cookies are expired without removing another session.
3. **Given** an explicit cross-site request, **when** logout is requested, **then** it is rejected without deleting a session or expiring authentication cookies.
4. **Given** a session on another device, **when** the current browser logs out, **then** that other session remains usable.

### Edge Cases

- Both ordinary and secure-prefixed legacy cookie names must be handled according to the configured origin; duplicates or malformed encodings cannot select a session for deletion.
- A valid signature is required before a cookie value can select a session. A public identity header, request body, or unsigned token cannot select a session.
- A database deletion failure follows the established best-effort sign-out response: clear browser cookies, return 204, and record a bounded diagnostic outcome without exposing the token.
- Missing signing configuration fails closed before attempting a session deletion.
- Responses must be non-cacheable, and any session-data cookie chunks must also be cleared.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The established logout request MUST be available at its exact public POST path and enforce the existing same-origin boundary.
- **FR-002**: Only a valid signed current-session cookie MUST be allowed to select a session for deletion; no public identity assertion is accepted.
- **FR-003**: Logout MUST affect at most the matching current session and MUST not revoke another device's session.
- **FR-004**: With valid configuration, logout MUST be idempotent and return an empty 204 response for present, absent, stale, or malformed cookies.
- **FR-005**: Logout MUST expire the established browser authentication cookies and any session-data chunks using matching security attributes, without returning or logging token values.
- **FR-006**: Explicit cross-site requests and missing required signing configuration MUST fail safely without deleting a session.
- **FR-007**: All responses MUST be non-cacheable; diagnostics MUST use bounded, non-sensitive outcomes.
- **FR-008**: Automated tests MUST cover signature compatibility, cookie variants and deletion, malformed/duplicate input, session isolation, origin rejection, dependency failure, and representative p95 latency.
- **FR-009**: Logout-all, other session management, frontend changes, protected-route authentication, schema changes, routing, deployment, and CI/CD are outside this slice.

### Key Entities

- **Current session**: The single session selected by a verified browser cookie.
- **Browser authentication cookies**: Session-token, cached-session and remember-state values cleared on sign-out.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every successful logout with a valid cookie removes exactly one matching session and leaves all other sessions unchanged.
- **SC-002**: No absent, malformed, duplicated, tampered, or cross-site cookie can remove a session.
- **SC-003**: At least 95% of representative logout requests finish within one second.
- **SC-004**: Changed production code has at least 80% line and branch coverage and passes local verification.

## Assumptions

- The installed Better Auth version and the existing PostgreSQL sessions table define the compatibility contract.
- The old service remains production route owner; Java-protected APIs still require signed bridge assertions.
- Best-effort cleanup on persistence failure preserves the old sign-out behavior; diagnostics reveal the failure without sensitive data.
