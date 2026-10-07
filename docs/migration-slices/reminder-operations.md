# Reminder Operations and Delivery (Feature 025)

Java now implements the current reminder create/edit/cancel/complete/snooze/reopen and
email-retry routes, owner preference updates, and the internal due-reminder delivery
route. Existing authenticated reads from feature 006 remain unchanged. The old service
is still the production route owner and only active scheduler; no traffic or scheduler
cutover is part of this stage.

User commands derive owner identity only from the verified browser session or signed
bridge. Application and stage associations are checked against that owner in SQL.
Stage-linked creation and suggestion resolution share one transaction. Edits and
state changes use positive versions and legal-state predicates; stale/illegal changes
do not write. Current verified recovery email is required when enabling email.

The internal route requires the configured `REMINDER_DELIVERY_SECRET` bearer value and
does not accept a browser session as authority. It bounds batch size and uses database
row locking, current-schedule uniqueness, claim tokens, expiring leases and attempt
limits. It invokes the existing mail webhook with the `scheduled_reminder` template,
then finalizes only the matching claim. Manual email retry queues a failed attempt;
it does not send mail during the user request. The Java scheduler has no automatic
activation in this slice. Operators must never run both schedulers against production
until a separate cutover plan assigns a single active owner.

The shared PostgreSQL schema and existing mail channel are unchanged. Local tests use
Testcontainers and fake mail, and cover owner isolation, validation, conflicts,
transitions, settings, retry, internal authentication, claim and finalization. Request
IDs and aggregate status/latency are safe health signals; do not log mail addresses,
secrets, task titles or message bodies. The local validation and p95 evidence are in
[`specs/025-reminder-operations/quickstart.md`](../../specs/025-reminder-operations/quickstart.md).

Rollback keeps or returns all affected traffic and scheduling to the old service.
There is no schema or data rollback. Activating Java traffic/scheduler, observing it,
and retiring old routes are separate operations outside this phase. No frontend,
CI/CD or deployment change is included.
