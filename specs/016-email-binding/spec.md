# Feature Specification: Account Email Binding

**Feature Branch**: `codex/016-email-binding`

**Created**: 2026-10-04

**Status**: Complete

**Input**: User request to submit 015 and begin 016. Migrate the existing authenticated email-binding command using a code already issued by the current service.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Bind a Verified Recovery Email (Priority: P1)

As a signed-in account owner, I can bind a recovery email after proving my current password and a valid code sent to that address. A code for another account or address cannot bind my account.

**Why this priority**: It completes the account-side email change after the 015 unbinding slice while preserving the existing code-delivery authority.

**Independent Test**: Seed an owner with a password, current session, and an active code issued for a target email. Bind it and verify the response, verified email state, consumed code, and unchanged foreign owner.

**Acceptance Scenarios**:

1. **Given** a signed-in owner and an active, correct email code, **When** the owner supplies that code and the current password, **Then** the target email is verified and bound, and the code cannot be reused.
2. **Given** a wrong password, **When** binding is requested, **Then** no code attempt is consumed and no email changes.
3. **Given** a wrong, expired, exhausted, already-consumed, foreign-owner, or foreign-email code, **When** binding is requested, **Then** the email remains unchanged and a safe code error is returned. An incorrect active code counts toward the established five-attempt limit.
4. **Given** an email bound to another account, **When** binding is requested, **Then** the conflict is reported without changing either account or consuming a valid code.
5. **Given** an absent, invalid, replayed, or request-mismatched trusted identity, **When** binding is requested, **Then** no state changes.

### Edge Cases

- Email is normalized to lowercase, has a valid address shape, and is at most 254 characters.
- Verification code is a six-digit string after trimming surrounding spaces.
- Current password is required and at most 128 characters.
- Missing, null, scalar, or incorrectly typed fields fail before mutation; unrelated object properties are ignored.
- The current session must be active and owned by the verified subject.
- More than five email changes within fifteen minutes are rate-limited.
- A missing code-signing secret or dependency failure fails safely.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: This slice MUST expose only the authenticated email-binding command.
- **FR-002**: The owner MUST come from a trusted assertion bound to exact PATCH method, path, and request identifier; the supplied current session MUST be active and owner-scoped.
- **FR-003**: The request MUST validate current password, target email, and verification code before mutation.
- **FR-004**: The existing five-per-fifteen-minute email-change limiter MUST be shared with unbinding.
- **FR-005**: Current password MUST match the owner's existing compatible credential.
- **FR-006**: The code MUST be checked using the existing secret-based representation, purpose, owner, address, expiry, and attempt limit.
- **FR-007**: A wrong active code MUST increment its attempt count; a correct code MUST be consumed together with the owner email update in one transaction.
- **FR-008**: Success MUST return normalized `email` and `emailVerified: true`.
- **FR-009**: A target email already owned by another account MUST produce the established `email_conflict` response without consuming a valid code.
- **FR-010**: Success and errors MUST be non-cacheable and diagnostics MUST use bounded labels without personal or secret data.
- **FR-011**: Automated tests MUST cover contract parity, authorization, compatibility, attempt limits, code replay, conflicts, rollback, owner isolation, safe failure, and representative p95 latency.
- **FR-012**: Code issuance and delivery, registration, password reset, login, frontend changes, schema changes, routing, deployment, production traffic, and CI/CD remain outside this slice.

### Key Entities

- **Owner credential and session**: Existing proof that the account owner requested binding.
- **Email verification code**: Purpose-bound, owner-bound, address-bound code with expiry, attempts, and consumption state.
- **Recovery email state**: Nullable address and verification state on the owner profile.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All success fixtures return the normalized bound email and verified status, with one code consumed.
- **SC-002**: Rejected identity, password, code, and conflict fixtures change no owner or foreign email state.
- **SC-003**: Wrong active code attempts increment exactly once and stop after five attempts; successful codes cannot be reused.
- **SC-004**: At least 95% of representative binding operations finish within one second.
- **SC-005**: Changed code meets the repository's 80% line and branch coverage floor and verification gates.

## Assumptions

- The legacy service continues issuing and delivering binding codes into the existing shared table.
- Java receives the same HMAC secret as the legacy service when this route is activated.
- The signed bridge and trusted session header remain the Java identity boundary.
