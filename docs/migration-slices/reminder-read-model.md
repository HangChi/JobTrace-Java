# Feature 006: private reminder read model

Feature 006 adds two owner-scoped Java reads: `GET /api/reminders` (active, completed,
or cancelled selection) and `GET /api/reminder-settings`. The signed identity bridge
remains disabled by default. There is no Java deployment, routing change, or production
traffic activation in this feature.

The existing JobTrace service remains the sole owner of browser sessions, scheduled-
reminder and preference writes, notification attempts, email transport, scheduling,
PostgreSQL schema changes, and production traffic. Java only reads the unchanged legacy
tables with parameterized SQL. No production Flyway migration, write endpoint, internal
delivery endpoint, email integration, or frontend route is added.

## Contract and isolation

- A trusted `BridgeIdentity` supplies the owner. Public owner headers and ordinary
  authenticated principals cannot select another owner. The bridge assertion is bound
  to the exact method, path, request ID, and one-time replay key.
- Reminder rows, joined application display fields, email-attempt state, recovery email,
  and preferences are all owner-bound. Only a current-schedule email attempt appears.
  The recovery address appears only when verified and available.
- Active reminders are grouped at one captured instant; completed and cancelled history
  never enters active groups. The legacy 200-item limit and ordering are preserved.
  Unknown status selections fall back to active. Reads do not advance stored status.
- Successful responses use `private, no-store`. Failure problems contain a request ID
  but no private values. Metrics have only bounded operation/outcome labels.

## Local validation

Run `./mvnw verify` with Java 21 and Docker. The synthetic fixtures and test-only
PostgreSQL 17 schema under `src/test/resources/` model the current runtime contract;
the older legacy feature 008 OpenAPI has documented `suggestions`/`history` drift.
Tests cover JSON parity, owner isolation, security, safe failures, architecture,
read-only surface, query counts, and 500 ms p95 budgets. The performance fixture
adds 250 owned reminders, warms each operation 10 times, and samples 40 times.

Local and CI verification are the completion criteria for this code slice. Deployment,
cutover, observation and rollback rehearsal would require a separate release decision.
Until then the existing service continues serving all production reminder traffic.
