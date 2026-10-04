# Feature Specification: Request Email Binding Code

**Feature Branch**: `codex/017-email-verification-code`

**Created**: 2026-10-04

**Status**: Complete

**Input**: Continue Java migration with stage 017, the existing authenticated request for an email-binding verification code.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Request a Binding Code (Priority: P1)

As a signed-in account owner, I can request a short-lived code at a new email address so I can complete the existing email-binding flow.

**Why this priority**: Stage 016 can redeem a code, but owners still depend on the old service to request one.

**Independent Test**: Request a code as an owner with an active session, inspect the delivery request and stored code, then complete stage 016 binding with the delivered code.

**Acceptance Scenarios**:

1. **Given** an authenticated owner and available delivery service, **when** a valid unclaimed address is submitted, **then** one six-digit code is delivered, is usable for ten minutes, and the response confirms the request without exposing the code.
2. **Given** a second request for the same owner and address after the cooldown, **when** the new code is issued, **then** previous active codes for that binding are unusable.
3. **Given** an address owned by another account, **when** a code is requested, **then** the request conflicts and no new code is issued or delivered.
4. **Given** an invalid identity, inactive or foreign session, malformed address, or exceeded limit, **when** a code is requested, **then** no new code is issued or delivered.
5. **Given** an unavailable or rejecting delivery service, **when** a code is requested, **then** a safe error is returned; no code appears in the response or diagnostics.

### Edge Cases

- Address is lowercased and at most 254 characters.
- The same address is limited to five requests per hour and one per minute; the source key is limited to 100 per hour. These limits are shared with the existing service.
- A request must not trust a client-provided account identifier, session ownership, or forwarded network address.
- Delivery can report an invalid recipient distinctly from an infrastructure failure.
- Configuration without a valid code-signing secret or delivery endpoint fails safely.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST accept only an authenticated request for an email-binding code at the established route.
- **FR-002**: The owner MUST be selected from a trusted, exact-method/path-bound assertion and an active owner session.
- **FR-003**: The system MUST validate and normalize the address before issuing a code.
- **FR-004**: The system MUST reject an address bound to another account.
- **FR-005**: The system MUST apply the existing source, address, and cooldown limits before issuing a code.
- **FR-006**: The system MUST invalidate previous active codes for the same owner, address, and purpose and store a new purpose-bound code that expires after ten minutes.
- **FR-007**: The system MUST deliver the code through the existing delivery contract without exposing it in the API response, logs, or metrics.
- **FR-008**: Success MUST return the established accepted response; validation, conflict, rate-limit, recipient, and delivery failures MUST return stable safe errors and non-cacheable responses.
- **FR-009**: Tests MUST cover ownership, exact assertion matching, rate limits, conflict, replacement, delivery, compatibility with binding, and representative p95 latency.
- **FR-010**: Registration codes, other mail templates, frontend, schema changes, routing, deployment, production traffic, and CI/CD remain outside this slice.

### Key Entities

- **Binding code**: A six-digit, owner- and address-bound secret with purpose, expiry, attempt count, and consumption state.
- **Recovery address**: The normalized target address, which may not belong to another account.
- **Delivery request**: A transient message addressed to the target email, containing the code and expiry duration.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Successful requests produce exactly one deliverable code that stage 016 accepts, with no code in the HTTP response.
- **SC-002**: Rejected identity, conflict, invalid address, and rate-limited requests produce zero deliveries and zero new usable codes.
- **SC-003**: Replacement makes prior active codes unusable while retaining a ten-minute validity window for the new code.
- **SC-004**: At least 95% of representative successful issuance operations complete within one second, excluding external delivery latency.
- **SC-005**: Changed production code maintains at least 80% line and branch coverage and all local verification gates pass.

## Assumptions

- The existing PostgreSQL code table, rate-limit function, and delivery HTTP contract remain available.
- The same signing secret used by stage 016 is configured for issuance.
- The existing service remains the production route owner until a separate routing decision.
