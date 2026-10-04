# Feature Specification: Verified Account Registration

**Feature Branch**: `codex/019-registration`

**Created**: 2026-10-04

**Status**: Complete

**Input**: Continue the Java migration after registration-code issuance with the existing account registration command.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create an Account with a Verified Email (Priority: P1)

As a prospective user, I can exchange a valid registration email code for an account with a username and password, then sign in through the existing login service.

**Why this priority**: Registration is the next dependency after issuing a registration email code.

**Independent Test**: Issue a registration code, register anonymously, inspect the account and verified email, and authenticate using the existing credential representation.

**Acceptance Scenarios**:

1. **Given** a valid unused code and valid username, password, and email, **when** the registration request is accepted, **then** one account with a verified recovery email is created and the response confirms creation without signing in.
2. **Given** an invalid, expired, exhausted, or previously used code, **when** registration is attempted, **then** no account is created and a field-specific code error is returned.
3. **Given** an existing username or recovery email, **when** registration is attempted, **then** a field-specific conflict is returned and no additional account remains.
4. **Given** an explicitly cross-site request, exceeded source limit, or failed configured human challenge, **when** registration is attempted, **then** no account is created.

### Edge Cases

- Username is trimmed, lowercased, three to 30 ASCII letters, digits or underscores; display name is optional and at most 100 characters.
- Password length is eight to 16 characters; verification code is exactly six digits after trimming; email is normalized and at most 254 characters.
- Registration is limited to five attempts per source per minute; untrusted forwarded addresses do not bypass the limit.
- A failed code counts toward the existing five-attempt code budget; a successful code is consumed even if a later uniqueness conflict occurs, matching the existing service.
- A configured challenge endpoint is mandatory for that environment; missing or rejected challenges fail closed.
- No session or credential token is issued, and secrets do not appear in responses or diagnostics.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The established registration request MUST be available anonymously with the existing same-origin boundary.
- **FR-002**: The request MUST validate and normalize all registration fields and return stable field-level validation errors.
- **FR-003**: The request MUST apply the established source rate limit and, when configured, verify the human challenge before processing registration.
- **FR-004**: Only a matching active registration-purpose code for the normalized email MUST authorize account creation; failed attempts MUST use the existing attempt limit and successful verification MUST consume the code.
- **FR-005**: The created user and credential MUST be compatible with the existing authentication service, have a verified recovery email, and have the ordinary user role without an active session.
- **FR-006**: Username and recovery-email conflicts MUST be reported by field; a failure during creation MUST leave no partial account.
- **FR-007**: Success MUST return the existing accepted confirmation and all responses MUST be non-cacheable.
- **FR-008**: Automated tests MUST cover validation, origin, limits, challenge, code attempts, persistence, conflicts, password compatibility, and representative p95 latency.
- **FR-009**: Login, session issuance, password reset, frontend changes, schema changes, production routing, deployment, and CI/CD remain outside this slice.

### Key Entities

- **Registration code**: A short-lived, purpose-bound proof for a normalized email, with expiry and attempt count.
- **User**: The new ordinary account with username, display name, internal authentication email and verified recovery email.
- **Credential**: The password representation associated with the user for the existing sign-in service.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A valid request creates exactly one usable account and no session; invalid or rejected requests create zero accounts.
- **SC-002**: Username and recovery-email conflicts identify the correct field; code failures never reveal the expected code.
- **SC-003**: At least 95% of representative successful registration operations complete within one second, excluding an external challenge call.
- **SC-004**: Changed production code has at least 80% line and branch coverage and passes local verification.

## Assumptions

- The legacy database schema and email-code verification function are available to Java without new migrations.
- The existing service remains the production route owner and performs login and session issuance.
- The existing password hash format is the interoperability contract.
