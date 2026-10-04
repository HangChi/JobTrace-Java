# Feature Specification: Account Email Unbinding

**Feature Branch**: `codex/015-email-unbinding`

**Created**: 2026-10-03

**Status**: Complete

**Input**: User request to begin feature 015 after feature 014. Migrate the existing authenticated email-unbinding operation as one independently verifiable identity slice.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Remove My Recovery Email (Priority: P1)

As a signed-in account owner, I can remove my bound recovery email after proving my current password. Any unused verification codes for my account become unusable, while another account remains untouched.

**Why this priority**: Removing an obsolete recovery destination protects account access and is independent of issuing new email codes.

**Independent Test**: Seed a verified owner email, password, active session, and pending codes. Submit the current password; verify the response, cleared email and verification status, consumed owner codes, and unchanged foreign account.

**Acceptance Scenarios**:

1. **Given** a verified owner with a current password, **When** the owner unbinds the email using that password, **Then** the email and verification state are cleared and pending owner codes are consumed.
2. **Given** a wrong password, **When** the owner requests unbinding, **Then** nothing changes and the established current-password error is returned.
3. **Given** a foreign owner and codes, **When** another owner unbinds, **Then** no foreign row or code changes.
4. **Given** absent, invalid, replayed, or request-mismatched trusted identity, **When** unbinding is requested, **Then** no state changes.

### Edge Cases

- A missing, expired, or foreign current session cannot authorize unbinding.
- A missing or malformed credential fails without mutation.
- A missing or non-string current password is rejected; unrelated request fields are ignored.
- An already-unbound owner receives the same success shape and pending owner codes are still consumed.
- More than five changes within fifteen minutes are rejected without mutation.
- A failure while consuming codes rolls back the email update.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Only the established authenticated email-unbinding operation MUST be exposed in this slice.
- **FR-002**: The owner MUST come from a trusted assertion bound to the exact DELETE method, path, and request identifier.
- **FR-003**: The request MUST identify a current, active session belonging to that owner.
- **FR-004**: The supplied current password MUST be a non-empty string of at most 128 characters and MUST match the owner's existing credential.
- **FR-005**: The existing owner-based email-change limit of five attempts in fifteen minutes MUST be enforced before password verification.
- **FR-006**: Success MUST clear the recovery email, its verified timestamp, and the email-verified flag; it MUST consume all pending verification codes belonging to that owner.
- **FR-007**: The email update and code consumption MUST be atomic and owner-scoped.
- **FR-008**: Success MUST return `email: null` and `emailVerified: false`.
- **FR-009**: Wrong passwords MUST return the established `invalid_password` error without revealing credential state.
- **FR-010**: Failure responses MUST be safe, private, and non-cacheable; metrics MUST use bounded labels without identity or secret values.
- **FR-011**: Automated tests MUST cover contract parity, rate limiting, owner isolation, rollback, authorization, diagnostics, and representative write latency.
- **FR-012**: Email binding, code issuance or delivery, registration, login, password reset, frontend changes, schema changes, routing, deployment, production traffic, and CI/CD are outside this slice.

### Key Entities

- **Owner credential**: Existing password representation used to prove the request.
- **Recovery email state**: Nullable destination and verified status on one owner.
- **Pending verification code**: Owner-associated unused code invalidated after unbinding.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every successful fixture returns the established response and clears only the requesting owner's email state and pending codes.
- **SC-002**: Every rejected fixture changes zero email fields and zero verification codes.
- **SC-003**: No test changes or discloses another owner's profile or code.
- **SC-004**: At least 95% of representative unbinding operations complete within one second.
- **SC-005**: Changed code satisfies the repository's 80% line and branch coverage floor and local verification gates.

## Assumptions

- The signed identity bridge remains the owner authority; current-session metadata is supplied by the trusted issuer and independently checked by Java.
- Existing credential and rate-limit database structures remain authoritative.
- A successful unbind does not change username, login identity, credential, or current session.
