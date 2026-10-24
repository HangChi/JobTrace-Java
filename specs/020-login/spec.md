# Feature Specification: Compatible Account Login

**Feature Branch**: `codex/020-login`

**Created**: 2026-10-04

**Status**: Implemented locally

**Input**: Continue the Java identity migration after verified registration with the established account login flow.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Sign In with Username or Verified Email (Priority: P1)

As an existing user, I can sign in with my username or verified recovery email and password, then continue to a safe page using a session recognized by the existing service.

**Why this priority**: The previous stage creates accounts but does not permit them to sign in through Java.

**Independent Test**: Register an account, sign in through Java with both identifiers, inspect its session and browser cookie, and use that cookie with the existing service's session format.

**Acceptance Scenarios**:

1. **Given** valid credentials for an enabled user, **when** a same-origin login succeeds, **then** one compatible seven-day session and signed, protected browser cookie are created; the response gives the ordinary or admin destination without exposing the token.
2. **Given** a verified recovery email, **when** it is supplied in mixed case with the correct password, **then** it resolves to the same account and signs in.
3. **Given** an unknown account, wrong password, unverified email, or disabled account, **when** login is attempted, **then** the same non-enumerating credentials error is returned and no new session is created.
4. **Given** a cross-site request, exceeded source limit, or failed configured human challenge, **when** login is attempted, **then** no session or cookie is issued.
5. **Given** a safe local return path, **when** login succeeds, **then** that path takes precedence over the role default; an external or malformed target cannot redirect the user away from the application.

### Edge Cases

- Identifier accepts a case-insensitive username or valid email. The old `username` request field remains an alias for `identifier`.
- Password must be a nonempty string of at most 128 characters; malformed requests return validation rather than an identity hint.
- Login is limited to ten source attempts per minute. An untrusted forwarded address cannot evade the default limit.
- The session is stored only after password and disabled-state checks, and the browser cookie uses the installed legacy format and attributes.
- Missing signing configuration fails closed without leaving a session. Existing cookie values are never accepted as proof of identity by this public command.
- Errors and diagnostics never include passwords, raw session tokens, or full identifiers.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The established login request MUST be available anonymously with its same-origin boundary, source limit, and optional human challenge.
- **FR-002**: The request MUST validate and normalize the identifier, password, and optional return target, including the old username-field alias.
- **FR-003**: Only an enabled account with a matching credential MUST receive a new session; email lookup MUST require a verified recovery email.
- **FR-004**: Unknown account, bad password, unverified email, and disabled account MUST have one stable, non-enumerating response.
- **FR-005**: A successful login MUST create a session and signed browser cookie accepted by the existing authentication service, with secure cookie attributes and no token in the JSON body.
- **FR-006**: The response MUST choose a safe requested destination or the ordinary/admin role default.
- **FR-007**: Successful and failed responses MUST be non-cacheable, with bounded metrics that exclude secrets and personal data.
- **FR-008**: Automated tests MUST cover validation, origins, rate limits, challenge, credential verification, disabled and email states, session and cookie compatibility, redirect safety, and representative p95 latency.
- **FR-009**: Logout, password reset, frontend changes, protected-route authentication, schema ownership, production routing, deployment, and CI/CD remain outside this slice.

### Key Entities

- **Credential**: The existing password representation associated with a user.
- **User**: An enabled or disabled account with role, username, and possibly verified recovery email.
- **Session**: A time-bounded token record and signed browser cookie used by the existing service.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every valid login creates exactly one session and one usable session cookie; every rejected login creates zero.
- **SC-002**: All credential-related denials have the same status and public code regardless of account existence or state.
- **SC-003**: At least 95% of representative successful logins finish within one second, excluding an external challenge call.
- **SC-004**: Changed production code has at least 80% line and branch coverage and passes local verification.

## Assumptions

- The installed Better Auth version and legacy PostgreSQL schema are the interoperability contract.
- The old service remains production route owner; Java-protected APIs continue to require their existing signed bridge assertion.
- Login does not alter account registration or the existing service's logout behavior.
