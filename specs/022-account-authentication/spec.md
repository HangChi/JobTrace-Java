# Feature Specification: Complete Account Authentication

**Feature Branch**: `codex/022-account-authentication`

**Created**: 2026-10-04

**Status**: Draft

**Input**: Complete the remaining Java account and authentication behavior in the fewest practical stages, preserving existing user journeys and security boundaries.

## User Scenarios & Testing

### User Story 1 - Use a signed-in browser session (Priority: P1)

A signed-in person can open their own protected account and business data in the Java application without a server-to-server credential. An administrator can access administrator-only data only while their current access remains valid.

**Why this priority**: The Java frontend cannot become usable until browser sessions can authenticate its protected requests.

**Independent Test**: Sign in, read a protected resource, revoke or expire the session, and confirm the same browser immediately loses access; a non-admin must not gain administrator access.

**Acceptance Scenarios**:

1. **Given** a valid current browser session, **when** its owner reads a protected resource, **then** only that owner's data is returned.
2. **Given** a revoked, expired, malformed, tampered, or disabled account session, **when** it requests protected data, **then** the request is rejected without revealing account details.
3. **Given** a valid browser session, **when** a state-changing request has an untrusted origin, **then** no state change occurs.
4. **Given** a valid administrator session, **when** its administrator access is removed, **then** subsequent administrator requests are rejected.

---

### User Story 2 - Recover a forgotten password (Priority: P2)

A person with a verified recovery email can request a reset link and choose a new password. The request response does not reveal whether the address belongs to an account.

**Why this priority**: Password recovery is necessary once Java owns account sign-in.

**Independent Test**: Request a link, consume it once within its lifetime, sign in with the new password, and confirm existing sessions are no longer usable.

**Acceptance Scenarios**:

1. **Given** either a known or unknown recovery address, **when** a reset is requested, **then** the same generic success response is returned.
2. **Given** a valid unexpired reset link, **when** a permitted new password is submitted, **then** the password changes and earlier sessions are revoked.
3. **Given** an invalid, expired, or already-used link, **when** a reset is attempted, **then** the password remains unchanged.

---

### User Story 3 - Manage the current profile and avatar (Priority: P3)

A signed-in person can read their profile and upload an image as their avatar without being able to modify another person's profile.

**Why this priority**: The existing profile update accepts an image URL but Java cannot yet produce one.

**Independent Test**: Read the current profile, upload a supported image, and update only the current user's avatar; reject oversized or misidentified content.

**Acceptance Scenarios**:

1. **Given** a signed-in person, **when** they read their profile, **then** current name, avatar, and recovery-address state are returned without secret fields.
2. **Given** a supported image no larger than 5 MiB, **when** it is uploaded, **then** an avatar URL is returned for that owner.
3. **Given** unsupported, oversized, or mismatched content, **when** it is uploaded, **then** nothing is stored and a clear validation error is returned.

### Edge Cases

- A request may contain duplicate or ambiguous cookies; no ambiguous session may authenticate.
- Session or account storage may be unavailable; protected requests must fail closed.
- A reset request may be repeated, raced, or delivered late; a token must be single-use and bounded by expiry.
- An image upload may fail after validation; storage errors must not be reported as successful uploads.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST authenticate protected browser requests only from a valid, unexpired, signed session tied to an enabled account.
- **FR-002**: The system MUST preserve the existing signed server-to-server identity route for callers already using it, without accepting public identity headers as proof.
- **FR-003**: The system MUST enforce current owner and administrator authorization from authoritative account state on each request.
- **FR-004**: The system MUST reject unsafe browser writes without trusted same-origin evidence and keep authenticated responses non-cacheable.
- **FR-005**: The system MUST provide a non-enumerating recovery request, a one-hour single-use reset link, password validation, and revocation of existing sessions after successful reset.
- **FR-006**: The system MUST let the current user read their own profile and upload only signature-matching PNG, JPEG, WebP, or GIF images up to 5 MiB.
- **FR-007**: The system MUST avoid recording session tokens, reset tokens, passwords, image contents, or full recovery addresses in diagnostics.

### Key Entities

- **Browser session**: A signed browser credential tied to one stored, expiring session and account.
- **Account**: The owner identity, current role, enabled state, and profile information.
- **Reset link**: A short-lived, single-use password recovery grant for one account.
- **Avatar object**: An owner-scoped image with a validated type and public URL.

## Success Criteria

### Measurable Outcomes

- **SC-001**: All tested invalid or revoked browser sessions are denied on their next protected request; all tested valid sessions reach only their own resources.
- **SC-002**: All tested untrusted-origin browser writes are denied before mutation.
- **SC-003**: A verified recovery user can complete the reset journey within five minutes; a reset link stops working after one use or one hour.
- **SC-004**: Supported avatar uploads at or below 5 MiB succeed; every tested oversized or signature-mismatched image is rejected.
- **SC-005**: Representative protected reads complete within 500 ms p95 and interactive writes within one second p95 under the documented local test load.

## Assumptions

- Existing account, session, credential, and verification storage remains the source of truth; this stage does not transfer schema ownership.
- Existing signed cookies and reset links already issued by the old service remain compatible during migration.
- Frontend pages are part of a later stage; this stage supplies backend account and authentication behavior only.
- CI/CD, deployment, and production routing are outside this stage.
