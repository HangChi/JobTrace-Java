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

Authentication is explicitly out of the first migration slices. Protected APIs require a separately specified bridge that validates a short-lived signature, issuer, audience, expiry, request identity, user ID, role, and access version. The Java service must only accept it through the trusted server-to-server path.

## Rollback

Prefer routing rollback over database rollback. Database changes must use expand-and-contract sequencing. Stop new writes, direct traffic back to the existing service, preserve new columns and audit records, and investigate using request IDs without logging sensitive content.

