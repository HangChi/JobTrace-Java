# Feature Specification: Reminder Operations and Delivery

**Feature Branch**: `codex/025-reminder-operations`
**Created**: 2026-10-07
**Status**: Implemented
**Input**: Complete the Java migration of reminder creation, editing, cancellation, completion, reopening, snoozing, email retry, preference updates and scheduled delivery while preserving the current product and data.

## User Scenarios & Testing

### User Story 1 - Manage application reminders (Priority: P1)

A signed-in job seeker creates a task for an owned application, edits its schedule and channel, and cancels it when no longer needed.

**Independent Test**: Create a reminder linked to an owned application and optional owned stage, edit it at its current version, and cancel it; repeat with foreign links, invalid times and stale versions.

**Acceptance Scenarios**:

1. **Given** an owned application and valid future schedule, **when** a reminder is created, **then** the same detail shape as the existing service is returned with version 1.
2. **Given** an active reminder at a current version, **when** it is edited, **then** its schedule and channel change atomically and its version advances.
3. **Given** a foreign application or stage, **when** creation is requested, **then** no reminder or suggestion resolution is written.
4. **Given** a stale version, **when** edit or cancellation is requested, **then** a conflict is returned and the row remains unchanged.

### User Story 2 - Complete, defer and reopen reminders (Priority: P2)

A signed-in job seeker completes an active reminder, moves its notification time later, or reopens a completed or cancelled reminder.

**Independent Test**: Perform each legal transition and verify returned state, version, timestamps and rejection of stale or illegal transitions.

**Acceptance Scenarios**:

1. **Given** an active reminder, **when** it is completed or cancelled, **then** the appropriate terminal timestamp and version are recorded.
2. **Given** an active reminder, **when** it is snoozed to a future notification time, **then** it is pending at that time with an advanced version.
3. **Given** a completed or cancelled reminder, **when** it is reopened with a valid future schedule, **then** it becomes pending and terminal timestamps clear.
4. **Given** a missing or foreign reminder, **when** it is snoozed, **then** it is not found; stale or illegal state transitions return conflict.

### User Story 3 - Adjust reminder defaults (Priority: P3)

A signed-in job seeker changes home visibility, view and default reminder choices without changing another account's settings.

**Independent Test**: Update all existing preference fields, read them back and verify strict validation and owner isolation.

**Acceptance Scenarios**:

1. **Given** valid settings, **when** they are saved, **then** subsequent reads return exactly those settings.
2. **Given** invalid or incomplete settings, **when** they are submitted, **then** nothing changes.
3. **Given** a missing account, **when** settings are saved, **then** a not-found response is returned.

### User Story 4 - Retry and deliver due reminder email (Priority: P4)

A signed-in job seeker retries a failed email, and an authorized internal scheduler claims due reminders, sends them through the current mail channel and records outcomes.

**Independent Test**: Queue a valid failed attempt, reject invalid retries, then run a bounded internal delivery batch with fake mail responses and verify claim, skip, send, fail and replay behavior.

**Acceptance Scenarios**:

1. **Given** a due email reminder with a failed current-schedule attempt, **when** its owner retries at the current version, **then** a retry is accepted once without immediately sending mail.
2. **Given** an invalid credential, **when** a delivery batch is requested, **then** no reminder is claimed or mailed.
3. **Given** a due reminder and verified recipient, **when** an authorized batch runs, **then** at most the configured batch size is claimed and send success or failure is recorded.
4. **Given** an unverified recipient, sent attempt or active lease, **when** delivery runs, **then** it is not sent again.

### Edge Cases

- Title, time, enum, version, identifier and email-channel inputs are bounded and validated before writes.
- Notification time must be in the future; new and reopened schedules cannot notify after their event, while snoozing may move beyond an event when the shared schema permits it.
- Email can be enabled only when the owner's recovery address is currently verified.
- Concurrent edits, repeated retry requests and competing delivery workers must not silently duplicate work.
- Stage-linked creation updates suggestion resolution in the same transaction as the reminder.
- Delivery failures remain failed attempts; storage failures never masquerade as sent email.
- Browser workflows retain current loading, empty, success and error states; this stage changes no UI.

## Requirements

### Functional Requirements

- **FR-001**: Preserve current authenticated reminder create, edit, cancel, complete, snooze, reopen, email-retry and preference-update routes, payloads and response shapes.
- **FR-002**: All user operations derive identity from a verified session or signed bridge, never caller-supplied owner headers; writes and returned data are owner-scoped.
- **FR-003**: Preserve current validation for title, schedule, channel availability, preference choices and optimistic version.
- **FR-004**: Preserve legal lifecycle transitions, timestamps and version increments; reject stale or illegal transitions without partial writes.
- **FR-005**: Preserve owned application/stage checks and atomic suggestion-resolution linking when a reminder is created from a stage.
- **FR-006**: Retry only a failed email attempt for the reminder's current schedule; never send synchronously on the user's retry request.
- **FR-007**: Internal delivery requires the configured secret, bounds its batch, uses leases and attempt limits, and records sent/failed/skipped outcomes without duplicate sends from a live claim.
- **FR-008**: Use the current email delivery channel and existing database schema; no data migration or production traffic change is part of this stage.
- **FR-009**: Tests cover owner isolation, validation, conflicts, transitions, retry, internal authentication, claim/finalization and dependency failures.

### Key Entities

- **Scheduled reminder**: Owner, application and optional stage link, task title, event/notification times, email choice, lifecycle status and version.
- **Reminder preferences**: Owner-specific home view and creation/snooze defaults.
- **Notification attempt**: Current-schedule email claim, lease, attempt count, recipient and delivery outcome.
- **Suggestion resolution**: Owner's record that a stage suggestion became a scheduled reminder.

## Success Criteria

- **SC-001**: All tested foreign identifiers and applications cause zero foreign writes or disclosures.
- **SC-002**: Every tested stale or illegal transition leaves the reminder and attempt state unchanged.
- **SC-003**: In concurrent-worker tests, a due schedule is sent no more than once while its claim lease remains valid.
- **SC-004**: Representative user reads complete within 500 ms p95 and writes within 1 s p95 under documented local load; bounded internal batches finish without blocking user requests.
- **SC-005**: Changed production code reaches at least 80% line and branch coverage.

## Assumptions

- Current Next.js behavior and the existing PostgreSQL tables are the compatibility source.
- The old service remains production route owner and the only active scheduler until a separate cutover; this stage changes Java code only, not frontend, CI/CD, deployment or scheduling configuration.
- The current recovery-email verification and mail webhook are reused; tests send no real mail.
