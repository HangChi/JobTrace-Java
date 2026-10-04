# Feature Specification: Account Password Change

**Feature Branch**: `codex/014-password-change`

**Created**: 2026-10-03

**Status**: Complete

**Input**: User description: "提交013，继续014". After profile updates, feature 014 migrates only the established authenticated password-change behavior: verify the current password, store a compatible replacement password, and sign out other devices while preserving the caller's current session. Password reset, registration, login, email binding, session issuance, frontend routing, deployment, and CI/CD remain outside this slice.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Change My Password Safely (Priority: P1)

As a signed-in account owner, I can replace my password only after proving the current password, and other devices are signed out so that a compromised session cannot continue using my account.

**Why this priority**: Password rotation is the next smallest owner-scoped identity write and has a clear security boundary independent from password recovery or email ownership.

**Independent Test**: Seed an owner with a known credential and multiple sessions, submit the current and replacement passwords from one verified current session, then prove the new credential is compatible, the old credential no longer verifies, the current session remains, all other owner sessions are removed, and foreign accounts remain unchanged.

**Acceptance Scenarios**:

1. **Given** an authenticated owner with a credential account and multiple sessions, **When** the owner supplies the correct current password and a valid replacement, **Then** the password changes, the current session remains active, all other owner sessions are revoked, and the established success message is returned.
2. **Given** an authenticated owner, **When** the current password is wrong, **Then** the request reports the established current-password error and changes neither the credential nor any session.
3. **Given** another account and its sessions, **When** an owner changes a password, **Then** no foreign credential or session is read, changed, or disclosed.
4. **Given** an absent, invalid, replayed, or request-mismatched trusted identity, **When** a password change is requested, **Then** no credential or session changes.

### Edge Cases

- Current password is required but may be longer than the replacement-password policy so an existing credential can still be verified.
- Replacement passwords shorter than 8 characters or longer than 16 characters are rejected before mutation.
- Missing, null, scalar, or incorrectly typed password fields are rejected without mutation.
- Additional request properties are ignored, matching the established object-parser behavior.
- A missing credential account, malformed stored hash, absent current session, or current session owned by another account fails safely without mutation.
- Successful and failed responses are private and not cacheable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The slice MUST expose only the established authenticated account password-change behavior.
- **FR-002**: The command MUST require a trusted owner assertion bound to the exact POST method, path, and request identifier; invalid assertions MUST fail closed.
- **FR-003**: The command MUST require bounded current-session metadata and MUST prove that the preserved session belongs to the verified owner.
- **FR-004**: The current password MUST be a non-empty string, and the replacement password MUST contain 8 through 16 characters.
- **FR-005**: Additional request properties MUST NOT alter the command or response.
- **FR-006**: The current password MUST be verified against the owner's credential account using the established password representation.
- **FR-007**: A valid replacement password MUST be stored in a representation accepted by the existing authentication authority.
- **FR-008**: A successful change MUST preserve the verified current session and revoke every other session belonging to that owner.
- **FR-009**: Credential replacement and other-session revocation MUST complete atomically; any failure MUST leave both unchanged.
- **FR-010**: Wrong current passwords MUST return code `invalid_password`, identify `currentPassword` as the invalid field, and disclose no credential details.
- **FR-011**: Missing or invalid credential/session state MUST fail safely without exposing whether another account or credential exists.
- **FR-012**: A successful response MUST return the established message: `密码已更新，其他设备已退出登录。`
- **FR-013**: Successful responses and errors MUST prevent shared or browser caching.
- **FR-014**: Diagnostics MUST use bounded operation, outcome, and latency categories and MUST NOT contain account identifiers, session identifiers, passwords, hashes, assertions, or request bodies.
- **FR-015**: Automated tests MUST cover response parity, password boundaries, wrong-password behavior, compatibility, atomicity, owner isolation, session preservation/revocation, authorization, dependency failure, safe diagnostics, and representative performance.
- **FR-016**: Password reset, registration, login/logout, email binding, session issuance/refresh, schema changes, frontend changes, deployment, routing, production traffic, and CI/CD are outside this slice.

### Key Entities

- **Password Change Command**: A verified owner, verified current session, current password, and policy-compliant replacement password.
- **Credential Account**: The owner's password-bearing authentication account whose stored representation remains compatible with the existing authentication authority.
- **Owner Session Set**: The current session to preserve and other owner sessions to revoke after a successful credential replacement.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Representative success and error fixtures achieve 100% asserted response and durable-state parity with the established behavior.
- **SC-002**: Across ownership and authorization tests, zero foreign credentials or sessions are changed or disclosed.
- **SC-003**: Every successful change preserves exactly the verified current session and removes all other owner sessions; every rejected request changes zero credentials and zero sessions.
- **SC-004**: Under a reproducible representative account data set, 95% of password changes complete within 1 second.
- **SC-005**: Changed production code maintains at least 80% line and branch coverage, with all local quality gates passing.

## Assumptions

- The signed identity bridge remains the trusted source of the owner identifier.
- The trusted legacy issuer supplies the opaque current-session identifier only after authenticating the caller; Java independently proves ownership before mutation.
- Credential accounts use the established `credential` provider and the existing compatible password representation.
- Preserving the current valid session provides the same user outcome as replacing it: the caller stays signed in and all other devices are signed out.
- The existing users, accounts, and sessions tables remain unchanged.
