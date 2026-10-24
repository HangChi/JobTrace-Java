# Feature Specification: Account Profile Update

**Feature Branch**: `codex/013-profile-update`

**Created**: 2026-10-03

**Status**: Complete

**Input**: User description: "提交并开始下一阶段". Following the session-management slices, feature 013 migrates only the established authenticated account profile update for display name and an already-hosted avatar URL. Avatar upload, email binding, password changes, authentication, session issuance, frontend routing, deployment, and CI/CD remain outside this slice.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Update My Public Profile (Priority: P1)

As a signed-in account owner, I can update my display name and choose or clear my avatar URL so that my visible identity remains current.

**Why this priority**: This is the smallest independently useful profile write and directly follows the owner-scoped identity slices without moving credential or file-storage authority.

**Independent Test**: Seed owned and foreign accounts, update one owner with a valid display name and each supported image state, then verify only that account changed and the response preserves the established recovery-email projection.

**Acceptance Scenarios**:

1. **Given** an authenticated owner and valid display name, **When** the owner updates the profile, **Then** the trimmed display name and optional avatar URL are saved and returned.
2. **Given** an existing avatar, **When** the owner supplies an empty or null image, **Then** the stored image is cleared and the response contains null.
3. **Given** another account exists, **When** an owner updates a profile, **Then** no foreign profile field changes or is disclosed.
4. **Given** an absent, invalid, replayed, or request-mismatched trusted identity, **When** an update is requested, **Then** no profile changes.

### Edge Cases

- Missing, null, blank, or over-100-character display names are rejected before mutation.
- Display-name surrounding whitespace is removed before length validation and storage.
- Image accepts an absolute URL, an empty string, null, or omission; empty, null, and omission clear the image, matching established behavior.
- Additional request properties are ignored, matching the established object parser.
- A missing owner row produces a safe not-found response.
- Successful and failed responses are private and not cacheable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The slice MUST expose only the established authenticated account profile update behavior.
- **FR-002**: The command MUST require a trusted owner assertion bound to the exact PATCH method, path, and request identifier; invalid assertions MUST fail closed.
- **FR-003**: The display name MUST be a string, trimmed, and contain 1 through 100 characters.
- **FR-004**: The optional image MUST be an absolute URL, an empty string, or null; empty, null, and omission MUST clear the stored image.
- **FR-005**: Additional request properties MUST NOT alter the command or response.
- **FR-006**: The command MUST update only the verified owner's display name, image, and modification timestamp.
- **FR-007**: A successful response MUST return the owner's identifier, normalized display name, normalized image, recovery email, and recovery-email verification state.
- **FR-008**: Missing owner data MUST return a safe not-found response without exposing any other account.
- **FR-009**: The update MUST be one parameterized, owner-scoped durable operation and MUST NOT read or modify unrelated accounts.
- **FR-010**: Successful responses and errors MUST prevent shared or browser caching.
- **FR-011**: Diagnostics MUST use bounded operation, outcome, and latency categories and MUST NOT contain account identifiers, names, email addresses, image URLs, assertions, or request bodies.
- **FR-012**: Automated tests MUST cover response parity, all image states, validation, owner isolation, authorization, surface limits, dependency failure, safe diagnostics, and representative performance.
- **FR-013**: Avatar upload/storage, email binding, password changes, registration, login/logout, session issuance/refresh, schema changes, frontend changes, deployment, routing, production traffic, and CI/CD are outside this slice.

### Key Entities

- **Profile Update Command**: A verified owner, normalized display name, and nullable avatar URL.
- **Profile Update Result**: The updated public profile fields plus the unchanged recovery-email projection.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Representative success and validation fixtures achieve 100% asserted response and durable-state parity with the existing behavior.
- **SC-002**: Across ownership and authorization tests, zero foreign account fields are changed or disclosed.
- **SC-003**: Valid updates and avatar clearing complete successfully in one attempt, while every invalid request changes zero rows.
- **SC-004**: Under a reproducible representative account data set, 95% of updates complete within 1 second.
- **SC-005**: Changed production code maintains at least 80% line and branch coverage, with all local quality gates passing.

## Assumptions

- The signed identity bridge remains the trusted source of the owner identifier.
- Avatar upload continues to produce hosted URLs in the legacy service; Java only stores a supplied URL.
- Recovery-email fields are read only for response compatibility and are never changed by this command.
- The existing users table and profile UI contract remain unchanged.
