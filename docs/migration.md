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

The applications read-only implementation is documented in
[`migration-slices/application-read-model.md`](migration-slices/application-read-model.md).
Feature 004 ends with local and CI verification; Java deployment and traffic
activation are unscheduled.

The private interview read slice is documented in
[`migration-slices/interview-read-model.md`](migration-slices/interview-read-model.md).
Feature 005 likewise has no Java deployment or production traffic activation.

The private reminder read slice is documented in
[`migration-slices/reminder-read-model.md`](migration-slices/reminder-read-model.md).
Feature 006 likewise leaves reminder writes, delivery and production traffic with the
existing service; no Java deployment is planned.

The private data export read slice is documented in
[`migration-slices/private-data-export.md`](migration-slices/private-data-export.md).
Feature 007 leaves imports, writes, sessions, schema ownership and production traffic with
the existing service; no Java deployment is planned.

The private job-market read slice is documented in
[`migration-slices/job-market-read-model.md`](migration-slices/job-market-read-model.md).
Feature 008 leaves collection, synchronization, projection refresh, all writes, sessions,
schema ownership, frontend routing, deployment, and production traffic with the existing
service.

The administrator-only read slice is documented in
[`migration-slices/admin-read-model.md`](migration-slices/admin-read-model.md). Feature 009 adds
summary, managed-user, user-detail, and audit-history reads while leaving access changes, sessions,
job-market administration, schema ownership, frontend routing, deployment, and production traffic
with the existing service.

The administrator access-change slice is documented in
[`migration-slices/admin-access-change.md`](migration-slices/admin-access-change.md). Feature 010
adds only the managed-user access PATCH command and delegates every concurrency, idempotency,
last-administrator, session-revocation, and audit invariant to the existing atomic database
operation. Session issuance, ordinary identity settings, job-market administration, schema
ownership, frontend routing, deployment, CI/CD, and production traffic remain unchanged.

The account-session read slice is documented in
[`migration-slices/session-read-model.md`](migration-slices/session-read-model.md). Feature 011
adds only the active owner-scoped session-list GET and current-device projection. Better Auth and
the existing service retain authentication, cookies, issuance, refresh, expiry, revocation, every
other identity/profile operation, schema ownership, frontend routing, deployment, CI/CD, and
production traffic.

The account-session revocation slice is documented in
[`migration-slices/session-revocation.md`](migration-slices/session-revocation.md). Feature 012
adds only owner-scoped DELETE on the same session path, using one atomic ID-and-owner deletion and a
non-disclosing not-found response. Better Auth and the existing service retain every other session
and identity operation, schema ownership, frontend routing, deployment, CI/CD, and production
traffic.

The account-profile update slice is documented in
[`migration-slices/profile-update.md`](migration-slices/profile-update.md). Feature 013 adds only
owner-scoped PATCH of display name and an already-hosted avatar URL. Avatar upload, email binding,
credentials, every other identity operation, schema ownership, frontend routing, deployment,
CI/CD, and production traffic remain unchanged.

The account-password change slice is documented in
[`migration-slices/password-change.md`](migration-slices/password-change.md). Feature 014 adds only
owner-scoped POST password replacement after compatible current-password verification, preserving
the verified current session and atomically revoking other owner sessions. Password reset, email
binding, login and registration, session issuance, schema ownership, frontend routing, deployment,
CI/CD, and production traffic remain unchanged.

The account-email unbinding slice is documented in
[`migration-slices/email-unbinding.md`](migration-slices/email-unbinding.md). Feature 015 adds only
owner-scoped DELETE of an existing recovery email after current-password verification. Pending
owner verification codes are consumed in the same transaction. Email binding and code delivery,
schema ownership, frontend routing, deployment, CI/CD, and production traffic remain unchanged.

The account-email binding slice is documented in
[`migration-slices/email-binding.md`](migration-slices/email-binding.md). Feature 016 adds only
owner-scoped PATCH of a recovery email after current-password and existing verification-code
checks. Code issuance and delivery, schema ownership, frontend routing, deployment, CI/CD, and
production traffic remain with the existing service.

The account-email binding-code slice is documented in
[`migration-slices/email-binding-code.md`](migration-slices/email-binding-code.md). Feature 017
adds only owner-scoped POST to request a short-lived binding code and deliver it through the
existing mail contract. Registration codes, other mail templates, frontend routing, schema
ownership, deployment, CI/CD, and production traffic remain unchanged.

The registration-email code slice is documented in
[`migration-slices/registration-email-code.md`](migration-slices/registration-email-code.md).
Feature 018 adds only anonymous POST to request a short-lived registration code with exact
same-origin and rate-limit checks. Account creation, code redemption, frontend routing, schema
ownership, deployment, CI/CD, and production traffic remain unchanged.

The verified registration slice is documented in
[`migration-slices/registration.md`](migration-slices/registration.md). Feature 019 adds only
anonymous POST to redeem a registration-purpose email code and create a compatible user and
credential without signing in. Login, session issuance, frontend routing, schema ownership,
deployment, CI/CD, and production traffic remain with the existing service.

The compatible login slice is documented in
[`migration-slices/login.md`](migration-slices/login.md). Feature 020 adds only anonymous POST to
verify a username or verified recovery email and issue one shared-format session and signed
browser cookie. The old service remains the production route owner; Java protected APIs still
require signed bridge assertions.

The compatible current-session logout slice is documented in
[`migration-slices/logout.md`](migration-slices/logout.md). Feature 021 adds only anonymous POST to
verify a signed browser session cookie, delete that one session best-effort, and expire the
established cookies. Other Java APIs do not accept the browser cookie as authorization.

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

Better Auth remains authoritative during incremental migration. The signed identity bridge v1
protects `GET /api/analytics/summary`, the two feature 004 application read routes,
the three feature 005 private interview/dialog read routes, and the two feature 006
private reminder/settings read routes.
The feature 007 bridge also covers only the exact two private export GET routes.
Feature 008 adds only the exact job-market campaign list GET and UUID-shaped detail GET.
Feature 009 adds only the exact administration summary, user list, opaque-ID user detail, and audit
event list GET paths, and additionally requires the verified administrator role.
Feature 010 adds only PATCH on the same opaque-ID administrator user path and requires the verified
administrator role; the database command independently revalidates the actor's current access.
Feature 011 adds only GET on `/api/profile/sessions`, requires the verified owner subject, and uses
bounded server-to-server current-session metadata only to derive the response marker after owner
and active-expiry filtering.
Feature 012 additionally protects only DELETE on `/api/profile/sessions`; its separately bound
assertion supplies the owner for one opaque, owner-scoped session deletion.
Feature 013 additionally protects only PATCH on `/api/profile`; the verified subject selects the
single profile row and no request field can select another account.
Feature 014 additionally protects only POST on `/api/profile/password`; the verified subject and
bounded current-session metadata select one owner credential transition, and Java independently
proves current-session ownership before mutation.
Feature 015 additionally protects only DELETE on `/api/profile/email`; the verified subject and
bounded current-session metadata select one owner email transition after compatible password
verification.
Feature 016 additionally protects only PATCH on `/api/profile/email`; the verified subject and
bounded current-session metadata select one owner email transition after compatible password and
purpose-bound verification-code checks.
Feature 017 additionally protects only POST on `/api/profile/email/code`; the verified subject
and bounded current-session metadata select one owner-bound code issuance after rate limits and
address ownership checks.
Feature 018 permits only POST on `/api/auth/email-code` without an owner assertion. The endpoint
checks browser origin and applies source and address limits before issuing a registration-purpose
code; all other identity routes retain their existing authentication boundary.
Feature 019 additionally permits only POST on `/api/auth/register` without an owner assertion.
It applies the same-origin check, source limit, and optional human challenge before redeeming a
registration code and creating one account. Other identity routes retain their boundary.
Feature 020 additionally permits only POST on `/api/auth/login` without an owner assertion. It
applies the same-origin check, login source limit and optional challenge before issuing a shared
session cookie. The cookie never authorizes other Java APIs by itself.
Feature 021 additionally permits only POST on `/api/auth/logout` without an owner assertion. It
requires same-origin and a valid session-cookie signature before deleting one session; missing or
invalid cookies receive an idempotent 204 with cookie expiry.
It requires a 30-second assertion bound to the exact method, path, and request ID. Java accepts
configured current/previous keys only, atomically consumes the one-time ID in shared Valkey, and
fails closed if validation or replay protection is unavailable. This does not authorize writes
or move session ownership to Java. The current legacy issuer targets only analytics; any later
application traffic activation needs a separately reviewed issuer and routing change.

## Rollback

Prefer routing rollback over database rollback. Database changes must use expand-and-contract sequencing. Stop new writes, direct traffic back to the existing service, preserve new columns and audit records, and investigate using request IDs without logging sensitive content.
