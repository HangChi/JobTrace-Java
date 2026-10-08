# Data Model

## Scheduled reminder

`id`, `owner_id`, `application_id`, nullable `source_stage_occurrence_id`, trimmed `title` (1–120), event/notification times, email choice, status (`pending`, `due`, `completed`, `cancelled`), terminal timestamps and positive version. Application and optional stage must belong to the owner/application. New/reopened schedules notify in the future and no later than event; snooze may move notification past event under the existing relaxed schema.

Legal transitions: pending/due -> completed/cancelled/pending (snooze); completed/cancelled -> pending (reopen). Edit is limited to pending/due. Every successful update advances version once. Stale or illegal transitions do not write.

## Preferences

Owner's home visibility, `scheduled`/`suggestions` view, `on_time`/`30m`/`1h`/`1d` lead, 30/60/1440-minute snooze default and email default. Update all fields together; missing owner is not found.

## Notification attempt

Unique `(reminder_id, scheduled_for, channel)` with state `claimed`, `sent`, `failed`, or `skipped`, claim token, lease, recipient, attempt count and outcome. Only current-schedule failed email is manually retriable. A live claim or sent attempt is never sent again.

## Suggestion resolution

Stage-linked creation upserts `(owner_id, stage_occurrence_id)` to `scheduled` and the new reminder ID in the same transaction. Unlinked creation does not create a resolution.
