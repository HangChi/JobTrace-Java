# Research Decisions

- Route and validation oracle: existing `src/modules/reminders/application/` service and contract files in the old service. Keep response shape and status codes.
- Persistence oracle: `postgres-reminder-repository.ts` and migrations `20260927000300_scheduled_reminders.sql` plus `20260927000400_relax_scheduled_resolution_reference.sql`. Use parameterized owner-scoped JDBC on the same schema, without migration. The later migration removes `notify_at <= event_at` to allow snoozing beyond the event.
- Reuse the shared mail webhook with template `scheduled_reminder` and the Java HTTP mail pattern. Tests send no real mail.
- Queue retry in a transaction so an absent failed attempt cannot advance the reminder version.
- Delivery claims use `FOR UPDATE SKIP LOCKED`, leases, current-schedule uniqueness and attempt caps. Finalize by claim token; do not log recipient or task text.
- Reuse the Java `Reminder` response model and read query rather than creating a second projection.
- The old service remains active route owner and scheduler until a separate cutover.
