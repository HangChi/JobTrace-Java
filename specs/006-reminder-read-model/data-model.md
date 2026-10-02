# Data Model: Private Reminder Read Model

All data remains owned and written by the existing service. These are immutable read projections; no new production table or migration is introduced.

## ScheduledReminder

| Field | Type / values | Read rule |
| --- | --- | --- |
| `id` | UUID string | Stable reminder identity |
| `applicationId` | UUID string | Associated owned application |
| `sourceStageOccurrenceId` | UUID string or null | May become null after stage deletion |
| `companyName`, `positionName` | string | From the owned associated application |
| `title` | string | Private task text; never a log/metric label |
| `eventAt`, `notifyAt` | UTC ISO date-time string | Existing timestamp representation |
| `emailEnabled` | boolean | Current channel choice, not delivery proof |
| `status` | `pending`, `due`, `completed`, `cancelled` | Stored lifecycle state; reads do not transition it |
| `version` | positive integer | Preserved for existing writer's optimistic concurrency |
| `emailStatus` | `claimed`, `sent`, `failed`, `skipped`, or null | Only current `notifyAt` schedule's email attempt |
| `completedAt`, `cancelledAt` | UTC ISO date-time string or null | Preserve nullable lifecycle timestamps |

`ownerId` is required in every storage predicate but never appears in the response. An application association must also be owner-consistent before its names are included.

## ReminderSummary

| Field | Type | Meaning |
| --- | --- | --- |
| `overdue` | `ScheduledReminder[]` | Active items stored as due or pending with `notifyAt <= readInstant` |
| `upcoming` | `ScheduledReminder[]` | Active pending items with `notifyAt > readInstant` |
| `history` | `ScheduledReminder[]` | Completed or cancelled items for the selected history state |
| `email` | `ReminderEmailAvailability` | Current owner's verified-address availability |

For active selection, `history` is empty. For completed/cancelled selection, `overdue` and `upcoming` are empty. The selected result contains at most 200 reminders, ordered by due/elapsed priority, then notification time ascending, then ID ascending. There is no total or pagination token.

## ReminderEmailAvailability

| Field | Type | Rule |
| --- | --- | --- |
| `available` | boolean | True only when current recovery email exists and is verified |
| `address` | string or null | Exact current verified address; null otherwise |

## ReminderPreferences

| Field | Type / values | Established default |
| --- | --- | --- |
| `homeEnabled` | boolean | `true` |
| `homeView` | `scheduled`, `suggestions` | `scheduled` |
| `defaultLead` | `on_time`, `30m`, `1h`, `1d` | `1h` |
| `defaultSnoozeMinutes` | `30`, `60`, `1440` | `30` |
| `emailDefault` | boolean | `false` |

These values are read from the authenticated owner's profile. The existing service remains the only writer.

## Relationships and boundaries

- One owner has many scheduled reminders and one reminder-preference projection.
- A reminder belongs to one application; an optional source stage is a reference, not a new ownership path.
- Notification attempts belong to one reminder and scheduled notification time. This slice observes, but never creates or advances, an attempt.
- System-generated progress suggestions remain in the existing analytics context and are not part of this projection.

## State transitions

None are authorized in feature 006. All create, edit, complete, cancel, snooze, reopen, preference update, retry, and delivery transitions remain in the existing service.
