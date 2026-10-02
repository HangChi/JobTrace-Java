# Feature Specification: Private Reminder Read Model Migration

**Feature Branch**: `codex/006-reminder-read-model`

**Created**: 2026-10-02

**Status**: Draft

**Input**: User description: "开始006". Per the migration roadmap, the next domain after private interviews is reminders. This first bounded slice covers private reminder and preference reads only; the existing service retains reminder writes and delivery.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See My Active Reminders (Priority: P1)

As a job seeker, I can see my own due and upcoming scheduled reminders together with their application context and current email-attempt state, so I know what to handle next.

**Why this priority**: The active overview is the primary reminder entry point and provides useful read-only value without moving notification or write ownership.

**Independent Test**: Given reminders for two owners with pending and due states, request the active overview for each owner and compare the groups, order, fields, and email availability with the existing service. Neither owner can observe the other's reminders or address.

**Acceptance Scenarios**:

1. **Given** owned reminders that are due and in the future, **When** the user opens the active overview, **Then** due reminders appear first and future reminders appear in ascending notification-time order, with a stable tie-breaker.
2. **Given** a pending reminder whose notification time has passed but whose stored state has not yet been advanced by the existing delivery service, **When** the overview is read, **Then** it appears in the due group without changing its stored state or sending a notification.
3. **Given** no active reminders, **When** the overview is read, **Then** the due and upcoming groups are empty and the user's email availability is still represented.

---

### User Story 2 - Review Completed and Cancelled Reminders (Priority: P2)

As a job seeker, I can switch to completed or cancelled reminder history to confirm what happened without mixing historical items into my active tasks.

**Why this priority**: History is part of the established reminder contract and helps users distinguish completed plans from outstanding work.

**Independent Test**: Prepare owned reminders in all lifecycle states and request each history selection; verify only the selected state appears, the active groups remain empty, and no other owner's records or counts leak.

**Acceptance Scenarios**:

1. **Given** completed reminders, **When** the user selects completed history, **Then** only completed items appear in the history collection with their completion time.
2. **Given** cancelled reminders, **When** the user selects cancelled history, **Then** only cancelled items appear in the history collection with their cancellation time.
3. **Given** an unknown history selection, **When** the user reads the overview, **Then** the established default active selection is used rather than exposing a new state.

---

### User Story 3 - Read My Reminder Defaults (Priority: P3)

As a job seeker, I can see my existing reminder display and creation defaults, plus whether my verified email is available, so current screens can present the same choices as before.

**Why this priority**: Preferences determine which reminder content appears on the home screen and how creation controls are pre-filled, but this slice need not change them.

**Independent Test**: Compare preference reads for users with customized and default values; verify one user's settings and verified email never appear in another user's response.

**Acceptance Scenarios**:

1. **Given** saved preferences, **When** the user opens reminder settings, **Then** the home visibility, home view, default lead time, default snooze duration, and default email choice match the existing service.
2. **Given** an account without customized preference values, **When** settings are read, **Then** the established defaults are returned.
3. **Given** an unverified or absent recovery email, **When** the user reads the reminder overview, **Then** email is shown as unavailable and no address is exposed.

### Edge Cases

- The active overview includes both stored-due items and pending items whose notification time has already passed; an exact-time boundary is handled consistently.
- Multiple reminders share the same notification time; their order remains stable between reads when data is unchanged.
- A reminder's linked application is missing or belongs to a different owner, or its source stage was removed; no foreign application data or stage existence is disclosed.
- A reminder has no notification attempt, a failed attempt, or a later attempt for an older schedule; the displayed email state reflects only the current schedule.
- A user has more reminders than the established overview cap; the same bounded subset and ordering are returned without an invented total or pagination contract.
- Email availability changes between reads, including verification being revoked; no stale address is exposed.
- Identity verification, replay protection, or storage is unavailable; the read fails safely without a fallback to caller-supplied owner information.
- The current browser interface must retain its loading, empty, success, and error behavior; this backend slice does not redesign screens or introduce new interaction states.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every replacement reminder read MUST use the reviewed trusted identity and MUST reject public owner headers or other caller-controlled identity claims.
- **FR-002**: Reminder rows, application context, notification-attempt state, email availability, and preferences MUST all be scoped to the authenticated owner.
- **FR-003**: The active overview MUST preserve the established due, upcoming, history, and email-availability fields, using empty collections rather than omitted fields.
- **FR-004**: Each reminder MUST preserve the established identity, application and optional source-stage links, company, position, title, event and notification times, email-enabled flag, lifecycle status, version, current-schedule email-attempt state, and nullable completion/cancellation times.
- **FR-005**: The active selection MUST include pending and due reminders, classify an overdue pending reminder as due at read time, and order due items before upcoming items by notification time with a stable identifier tie-breaker.
- **FR-006**: Completed and cancelled selections MUST return only their respective histories; due and upcoming groups MUST be empty for either history selection.
- **FR-007**: An omitted or unrecognized selection MUST follow the established active default; no new lifecycle selection or state transition is introduced.
- **FR-008**: The overview MUST return at most 200 reminders in the established order and MUST NOT invent a total, cursor, or page field absent from the existing contract.
- **FR-009**: Email availability MUST expose the address only if the owner's current recovery email is verified; otherwise it MUST report unavailable with no address.
- **FR-010**: The email-attempt state shown for a reminder MUST correspond to its current scheduled notification time; an old schedule's attempt MUST NOT be mistaken for the current one.
- **FR-011**: Preference reads MUST preserve home visibility, selected home reminder view, default lead time, default snooze duration, and default email choice, including established defaults where values are absent.
- **FR-012**: Representative active, due, upcoming, completed, cancelled, empty, default-preference, and email-availability results MUST be compared against the existing service for all asserted fields and ordering.
- **FR-013**: Unauthorized and dependency-failure responses MUST use the project's safe error form and request identifier without exposing reminder text, email addresses, assertions, or storage details in logs or diagnostics.
- **FR-014**: The replacement reads MUST expose bounded outcome and latency diagnostics without user IDs, reminder IDs, titles, addresses, or query values as labels.
- **FR-015**: This feature MUST be read-only. The existing service remains the sole owner of reminder creation, editing, completion, cancellation, snoozing, reopening, preference updates, notification attempts, email retry, and scheduled delivery.
- **FR-016**: This feature MUST NOT alter the existing data history or ownership, or change production traffic.
- **FR-017**: Existing browser wording, display order, empty/error feedback, and accessibility behavior MUST remain unchanged; no UI redesign is part of this slice.

### Key Entities *(include if feature involves data)*

- **Scheduled Reminder**: An owner's planned application-related task with an event time, next notification time, channel choice, lifecycle status, and version.
- **Reminder Overview**: The bounded, ordered due/upcoming or historical reminder collections plus current email availability.
- **Notification Attempt State**: The current schedule's email processing state, if one exists; it does not prove final mailbox delivery.
- **Reminder Preferences**: The owner's home display choice and defaults for new reminders and snoozing.
- **Verified Email Availability**: Whether the owner's current recovery address is verified and may be displayed as a possible reminder channel.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Representative reminder overviews and preference responses match the existing service for 100% of asserted fields, null/empty forms, selected states, and ordering.
- **SC-002**: Isolation checks disclose zero reminders, application details, email addresses, preference values, or notification states belonging to another owner.
- **SC-003**: At a fixed clock, 100% of tested notification-time boundary cases are classified into the correct due or upcoming group without changing reminder state.
- **SC-004**: With the established maximum overview size and representative associated records, 95% of reminder and preference reads complete within 500 milliseconds.
- **SC-005**: Automated checks cover normal, empty, history, invalid identity, cross-owner, unverified email, missing/current notification attempt, and unavailable-dependency cases without production credentials.
- **SC-006**: A maintainer can validate the slice from a clean checkout without sending an email, running a delivery job, deploying the replacement service, or changing production traffic.

## Assumptions

- “开始006” refers to the next domain in the Java migration roadmap, reminders. Following features 004 and 005, this first 006 slice migrates existing private reads before any write or delivery ownership decision.
- The existing service remains the contract oracle and sole writer. Its current overview has active/completed/cancelled selections, a 200-item cap, and a notification-time/identifier order; planning must verify these details against the current source before implementation.
- Feature 003's reviewed identity bridge is the only trusted private-read boundary; enablement remains opt-in and any traffic activation requires separate authorization.
- The existing analytics progress suggestions are distinct from user-scheduled reminders and remain with the current analytics behavior. Converting, completing, or dismissing suggestions is not part of 006.
- No new browser workflow or accessibility behavior is required because this slice replaces reads only. Future UI changes must be specified and tested separately.
- As previously decided, the replacement service will not be deployed in this phase.
