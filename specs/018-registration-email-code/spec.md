# Feature Specification: Request Registration Email Code

**Feature Branch**: `codex/018-registration-email-code`

**Created**: 2026-10-04

**Status**: Complete

**Input**: Continue the Java identity migration with the existing anonymous registration-code request.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Request a Registration Code (Priority: P1)

As a prospective user, I can request a short-lived code at my email address before creating an account.

**Why this priority**: Registration requires an email code, and the preceding stage migrated the related code-delivery mechanism for existing accounts.

**Independent Test**: Submit a valid unclaimed address without signing in, capture the delivered code, and confirm a registration-purpose code is active without affecting binding codes.

**Acceptance Scenarios**:

1. **Given** a valid unclaimed address, **when** a same-origin request is accepted, **then** one six-digit registration code is delivered and remains valid for ten minutes; the response contains only a confirmation.
2. **Given** a previous active registration code for the address, **when** another request is accepted after cooldown, **then** the previous registration code is unusable and a new code is delivered.
3. **Given** an address already bound to an account, **when** a code is requested, **then** the request conflicts with a field-specific error and no code is delivered.
4. **Given** an invalid address, cross-site browser request, exceeded limit, or unavailable delivery, **when** a code is requested, **then** no successful confirmation is returned and no secret appears in the response or diagnostics.

### Edge Cases

- The address is normalized to lowercase, has a valid shape, and is at most 254 characters.
- A source is limited to 100 requests per hour, an address to five per hour, and an address to one per minute; address limits are shared with the existing service.
- The route is anonymous but must reject an explicitly cross-site request or a mismatched Origin.
- A malformed body, scalar or null address, and missing signing or delivery configuration fail safely.
- Invalid-recipient feedback is distinct from other delivery failures.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The established registration-code request MUST be available without a signed-in session.
- **FR-002**: The request MUST enforce the existing browser-origin boundary before issuing a code.
- **FR-003**: The address MUST be validated and normalized; an address bound to an account MUST be rejected with a stable field-specific conflict.
- **FR-004**: The existing source, address, and cooldown rate limits MUST apply before issuance.
- **FR-005**: A successful request MUST invalidate prior active registration codes for the same address, without changing binding codes, then store a new ten-minute registration code.
- **FR-006**: The code MUST use the existing secret-based representation and delivery contract; plaintext MUST not be returned or logged.
- **FR-007**: Success MUST return the existing accepted confirmation; failures MUST be safe and non-cacheable.
- **FR-008**: Tests MUST cover the anonymous security boundary, conflict, limits, replacement, purpose isolation, delivery, compatibility, and representative p95 latency.
- **FR-009**: Account creation, login, code redemption, frontend changes, schema changes, routing, deployment, production traffic, and CI/CD remain outside this slice.

### Key Entities

- **Prospective email address**: A normalized address not currently bound to an account.
- **Registration code**: A purpose-bound secret with address, expiry, attempt limit, and consumption state; it is not bound to a user.
- **Delivery request**: A transient message containing the code and ten-minute expiry.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Accepted requests deliver exactly one registration-purpose code and never include the code in the HTTP response.
- **SC-002**: Rejected origin, conflict, invalid address, and rate-limited requests deliver zero codes.
- **SC-003**: Replacement leaves exactly one active registration code for the address and does not alter binding codes.
- **SC-004**: At least 95% of representative successful issuance operations complete within one second, excluding external delivery latency.
- **SC-005**: Changed production code maintains at least 80% line and branch coverage and passes local verification.

## Assumptions

- The existing registration service remains responsible for code redemption and account creation.
- The same secret and email-delivery endpoint used by the existing service can be configured for Java.
- The existing service continues to own production routing until a separate decision.
