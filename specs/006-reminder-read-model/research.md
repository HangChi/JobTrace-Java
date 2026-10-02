# Research: Private Reminder Read Model

## Legacy contract and source of truth

**Decision**: Treat the current service's running read paths and repository mapper as the behavioral oracle. Preserve `GET /api/reminders` and `GET /api/reminder-settings` only.

**Evidence**: The source routes live in `src/app/api/reminders/route.ts` and `src/app/api/reminder-settings/route.ts`; read logic and row mapping live in `src/modules/reminders/application/reminder-service.ts`, `application/contracts.ts`, and `infrastructure/postgres-reminder-repository.ts` in the existing JobTrace repository. The existing feature 008 OpenAPI file describes reminders but its `ReminderSummary` still requires `suggestions` while the runtime and contract test return `history`; it also omits the preferences read. Capture fresh synthetic runtime fixtures and record this drift before implementing Java.

**Alternative considered**: Copy the older OpenAPI schema literally. Rejected because it disagrees with current response behavior and would force a browser-contract regression.

## Overview selection, ordering, and time

**Decision**: Preserve the source's `active`, `completed`, and `cancelled` selections, defaulting unknown or absent values to `active`. Active includes stored `pending` and `due`; history selections include only their named status. Return at most 200 rows. Sort stored-due or elapsed notifications first, then by `notify_at` ascending and UUID ascending. Use one injected clock instant for both ordering and response grouping in the replacement, so the exact-time boundary cannot move during a request.

**Evidence**: The existing route normalizes the query value; the repository selects statuses, orders with a due/elapsed case expression and `notify_at, id`, limits 200, then groups active items by status or current time.

**Alternative considered**: Trust stored `status` alone. Rejected because a notification may be due before the legacy delivery worker advances its stored state. Introducing pagination is deferred because the current contract has none.

## Ownership and privacy

**Decision**: Reuse feature 003's signed, request-bound, one-time identity bridge and feature 005's trusted owner extraction. Bind owner in every reminder, application, user-preference, email-availability, and attempt read. Require an owned application for its display names; an inconsistent cross-owner association must not reveal the other application.

**Evidence**: The legacy query filters reminder owner; email and preference queries use user ID. Its application join does not separately bind application owner, so defense-in-depth is needed in Java. No owner header or ordinary principal is sufficient.

**Alternative considered**: Add only a controller-level owner check. Rejected because the row's joins and subqueries could still expose another owner's data if database associations are inconsistent.

## Notification state and verified email

**Decision**: Read the most recent attempt only for the reminder's *current* notification time and email channel, owner-bound. Return null if absent. Expose a recovery email address only when current verification exists; otherwise return unavailable/null. Preserve the source's `claimed`, `sent`, `failed`, and `skipped` attempt-state values without claiming final mailbox delivery.

**Evidence**: The legacy lateral attempt query filters `scheduled_for = notify_at`; `emailAvailability` uses `recovery_email_verified_at` and address presence. The database has a unique `(reminder_id, scheduled_for, channel)` constraint.

**Alternative considered**: Show any latest attempt for the reminder. Rejected because a snoozed reminder could display an older schedule's delivery result.

## Preference defaults

**Decision**: Preserve the five persisted user preferences and source defaults: home enabled `true`, home view `scheduled`, lead `1h`, snooze `30` minutes, email default `false`. Return only the trusted owner's values. Do not alter them or validate email eligibility by mutating a setting.

**Evidence**: `src/modules/reminders/infrastructure/postgres-reminder-repository.ts` and the existing reminder-preferences migration use the same values.

**Alternative considered**: Recalculate or rewrite defaults in this slice. Rejected because the current service remains the settings writer and a read migration must not alter user choices.

## Delivery and verification boundary

**Decision**: Add two protected, no-store GET reads in the existing modular monolith, with explicit JDBC queries against the unchanged schema. Keep Flyway disabled; do not add reminder writes, internal delivery, email integration, background jobs, frontend changes, deployment, or production routing. Use synthetic contract fixtures, fixed-clock unit tests, PostgreSQL integration/isolation tests, signed-identity tests, and 500 ms p95/constant-query checks. Maintain 80% line and branch coverage for changed production code.

**Evidence**: Features 003–005 establish the identity, read-model, safe-problem, testing, metrics, and no-deployment patterns. The constitution requires test and performance gates, and the project roadmap puts reminders after interviews.

**Alternative considered**: Migrate reminder writes and email delivery with this feature. Rejected because that transfers a scheduled side-effecting workflow and sole-writer responsibility well beyond a bounded first read slice.
