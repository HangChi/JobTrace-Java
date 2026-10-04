# Feature Specification: Administrator Access Changes

**Feature Branch**: `codex/010-admin-access-change`

**Created**: 2026-10-03

**Status**: Complete

**Input**: User description: "在新的分支开始010". Following the migration roadmap and feature 009, feature 010 migrates the single administrator account-access change command while preserving the established authorization, concurrency, idempotency, audit, and session-revocation behavior. Identity sessions, ordinary account settings, schema ownership, job-market administration, frontend routing, deployment, and production traffic remain outside this slice.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Change Account Access Safely (Priority: P1)

As an administrator, I can promote, demote, disable, or re-enable a managed account so that access is corrected without bypassing safety rules.

**Why this priority**: This is the only established account-access mutation and the direct continuation of the administration read model.

**Independent Test**: Starting from known account states, execute each allowed action with the current access version and a meaningful reason; verify the new state, incremented version, append-only audit result, and session revocation when disabling an account.

**Acceptance Scenarios**:

1. **Given** an active ordinary account, **When** an administrator promotes it with its current version and a valid reason, **Then** it becomes an administrator, its access version advances once, and one successful audit event is recorded.
2. **Given** an active account with sessions, **When** an administrator disables it, **Then** it becomes disabled, its access version advances once, all of its sessions are revoked, and the audit event records the before and after states.
3. **Given** a disabled account, **When** an administrator enables it, **Then** the account becomes active without restoring revoked sessions.

---

### User Story 2 - Retry Without Duplicate Effects (Priority: P2)

As an administrator, I can safely retry a request after an uncertain response so that the action is not applied or audited twice.

**Why this priority**: Access changes are security-sensitive and clients must be able to recover from timeouts without guessing whether a command completed.

**Independent Test**: Submit the same request identifier and identical command repeatedly and concurrently; verify every response refers to the original result, only one state transition occurs, and only one audit event exists.

**Acceptance Scenarios**:

1. **Given** a completed access change, **When** the identical request is retried, **Then** the original result is returned as replayed without another version increment or audit event.
2. **Given** two identical requests arrive concurrently, **When** they complete, **Then** one performs the transition and the other returns the same recorded result.
3. **Given** a request identifier was already used, **When** it is reused for different command content, **Then** the command is rejected as an idempotency conflict and no new state change occurs.

---

### User Story 3 - Understand Rejected Changes (Priority: P3)

As an administrator, I receive a safe, actionable conflict response when a requested change cannot proceed so that I can refresh state or provide the required confirmation.

**Why this priority**: Optimistic concurrency and last-administrator protection prevent dangerous or stale changes, but operators still need a clear recovery path.

**Independent Test**: Exercise stale versions, invalid state transitions, self-demotion or self-disable without confirmation, last-active-administrator protection, unknown targets, and non-administrator callers; verify stable problem codes, latest safe state when available, and no unauthorized disclosure.

**Acceptance Scenarios**:

1. **Given** a stale expected version, **When** a command is submitted, **Then** it is rejected with the latest access state and an audit event identifying the conflict.
2. **Given** the actor targets their own administrator access, **When** demotion or disabling lacks explicit confirmation, **Then** it is rejected without changing access.
3. **Given** the target is the last active administrator, **When** demotion or disabling is requested, **Then** it is rejected even when self-confirmation is present.
4. **Given** a non-administrator or invalid identity, **When** an access command is attempted, **Then** no account or audit information is returned and nothing changes.

### Edge Cases

- The target does not exist or its opaque identifier is empty or outside the supported length.
- The action is unknown, the expected version is not positive, the request identifier is malformed, or the trimmed reason is shorter than 10 or longer than 500 characters.
- An action does not match the current account state, such as promoting an administrator or enabling an active user.
- The actor becomes disabled or loses administrator status between assertion issuance and command execution.
- The dependency fails before or after the durable command result is recorded.
- A response retry uses the same request identifier after the target account has subsequently changed again.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The feature MUST support exactly four access actions: promote administrator, demote administrator, disable account, and enable account.
- **FR-002**: The command MUST require a current trusted administrator identity bound to the exact command method, target path, and request identifier; absent, ordinary, disabled, forged, expired, replayed, method-mismatched, and path-mismatched identities MUST fail closed.
- **FR-003**: The command MUST accept a unique request identifier, a positive expected access version, one supported action, a trimmed reason of 10–500 characters, and optional explicit self-confirmation.
- **FR-004**: The feature MUST reject empty or unsupported target identifiers and unknown targets without exposing unrelated account data.
- **FR-005**: Each successful command MUST apply exactly one state transition and increment the target access version exactly once.
- **FR-006**: Disabling an account MUST revoke all current sessions for that account in the same durable operation; enabling it MUST NOT recreate sessions.
- **FR-007**: Demoting or disabling the last active administrator MUST be rejected.
- **FR-008**: Demoting or disabling the acting administrator's own account MUST require explicit confirmation and MUST still preserve the last-active-administrator rule.
- **FR-009**: A stale expected version or action incompatible with current state MUST be rejected without changing the account.
- **FR-010**: Every authorized command attempt that reaches the protected access-change operation MUST produce one append-only audit result containing the actor and target snapshots, action, outcome, trimmed reason, before state, after state, and stable failure code when applicable.
- **FR-011**: Retrying an identical request identifier and identical command MUST return the original recorded result without another state transition, session deletion, or audit event.
- **FR-012**: Reusing a request identifier for different command content MUST be rejected as an idempotency conflict.
- **FR-013**: Concurrent identical requests MUST converge on one durable transition and one audit event.
- **FR-014**: A successful response MUST return the latest managed-user summary, audit event identifier, and whether the result was replayed.
- **FR-015**: Conflict responses MUST use stable codes and include the audit event identifier and latest safe access state when available.
- **FR-016**: Responses and errors MUST be private and non-cacheable, retain the effective request identifier, and MUST NOT expose credentials, assertions, reasons in logs, or database diagnostics.
- **FR-017**: Metrics MUST use bounded operation and outcome labels and MUST NOT contain account identifiers, request identifiers, reasons, or response bodies.
- **FR-018**: The feature MUST reuse the established atomic access-change policy and append-only audit mechanism so there is only one implementation of security invariants.
- **FR-019**: Ordinary account settings, authentication/session issuance, schema changes, job-market administration, frontend changes, deployment, routing, and CI/CD are outside this slice.

### Key Entities

- **Access Change Command**: A uniquely identified request containing the target, expected version, action, reason, and self-confirmation choice.
- **Access State**: The target account's role, disabled flag, and monotonically increasing access version.
- **Access Change Result**: The outcome, stable failure code, audit identifier, replay status, and latest safe access state.
- **Administration Audit Event**: The immutable record of the actor, target, requested action, reason, outcome, and before/after snapshots.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All four supported actions produce the established account, session, and audit outcomes across success and rejection scenarios.
- **SC-002**: Across repeated and concurrent retry tests, one logical request produces exactly one access-version increment and one audit event.
- **SC-003**: Across authorization tests, zero account or audit fields are returned to non-administrators or invalid identities and zero durable records change.
- **SC-004**: Last-active-administrator, self-confirmation, stale-version, invalid-state, and idempotency protections succeed in 100% of automated scenarios.
- **SC-005**: Under a reproducible representative dataset, 95% of access-change requests complete within 1 second.
- **SC-006**: Maintainers can verify the slice locally without changing production schema, frontend behavior, deployment, routing, or CI/CD configuration.

## Assumptions

- Feature 010 is the bounded account-access command deferred by feature 009 and precedes broader identity/session migration.
- The established atomic access-change operation remains the authoritative implementation of concurrency, idempotency, last-administrator, audit, and session-revocation rules.
- The current service remains the browser session issuer, account-settings writer, schema owner, frontend owner, deployment owner, and live traffic owner.
- Opaque user identifiers remain case-sensitive and at most 128 characters.
