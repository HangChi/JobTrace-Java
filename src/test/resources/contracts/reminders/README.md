# Reminder read fixtures

All IDs, addresses, names, titles and times in `read-fixtures.legacy.json` are synthetic. They are based on the current response shape and semantics of the existing service, not exported user data.

Current-service source locations in `/Users/songhangchi/Project/JobTrace`:

- `src/app/api/reminders/route.ts`: GET selection fallback and no-store response.
- `src/app/api/reminder-settings/route.ts`: GET preference response.
- `src/modules/reminders/application/contracts.ts`: response fields and enum values.
- `src/modules/reminders/infrastructure/postgres-reminder-repository.ts`: 200-item cap, due/elapsed ordering, current-schedule attempt and verified-email reads.
- `supabase/migrations/20260927000300_scheduled_reminders.sql` and `20260927000500_reminder_preferences.sql`: storage constraints/defaults.

The older `specs/008-scheduled-reminders/contracts/openapi.yaml` in that repository is stale for these GETs: its reminder summary still mentions `suggestions` instead of runtime `history`, and it omits `/api/reminder-settings`. The current routes and repository mapper are the contract oracle. Normalize timestamps to UTC millisecond ISO strings. Do not copy production rows or email addresses into fixtures.

The baseline in `read-fixtures.legacy.json` assumes `2026-10-02T00:00:00Z` as the fixed read instant. The `due-boundary` scenario is the same exact-instant classification as `active`. Contract tests should assert complete structural equality, not just selected fields. Error parity compares status and stable code while retaining Java's safe problem envelope.
