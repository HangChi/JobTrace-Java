# Migration Guide

## Strategy

Use incremental replacement. The existing JobTrace application remains available while bounded capabilities move to this repository.

Recommended order:

1. Health, errors, configuration, and observability
2. Analytics read-only pilot
3. Applications
4. Interviews
5. Reminders
6. Data transfer
7. Job market and background synchronization
8. Administration
9. Identity and access

## Slice checklist

Every migration slice must:

1. Record the current HTTP, authorization, error, and persistence contract.
2. Add representative fixtures and executable acceptance tests.
3. Reuse existing PostgreSQL functions where they protect invariants.
4. Identify the sole writer for each affected aggregate.
5. Provide health signals and safe, non-sensitive logs.
6. Exercise the rollback route before production activation.
7. Observe the new implementation before deleting the old one.

## Database baseline

Do not enable Flyway against a legacy database until all of these are complete:

- Existing migrations replay successfully into an empty PostgreSQL 17 database.
- Existing migration checksums and head version are recorded.
- Adoption is rehearsed against a restored production-like database.
- New migrations begin at an agreed version after the legacy head.
- Backup and restore are verified.
- Both repositories agree which one owns schema changes during transition.

## Authentication

Better Auth remains authoritative during incremental migration. The signed identity bridge v1 is
limited to `GET /api/analytics/summary`: legacy validates the browser session, reads fresh account
state, and issues a 30-second request-bound assertion. Java accepts configured current/previous keys
only, atomically consumes the one-time ID in shared Valkey, and fails closed if validation or replay
protection is unavailable. This does not authorize other routes or move session ownership to Java.

## Rollback

Prefer routing rollback over database rollback. Database changes must use expand-and-contract sequencing. Stop new writes, direct traffic back to the existing service, preserve new columns and audit records, and investigate using request IDs without logging sensitive content.
