# Feature 010: administrator access changes

Feature 010 adds compatibility for the single established account-access command:
`PATCH /api/admin/users/{id}`. A verified current administrator can promote, demote, disable, or
enable a managed account with an idempotency UUID, expected access version, meaningful reason, and
explicit self-confirmation when required.

Java validates the transport contract and calls the existing PostgreSQL
`change_user_access_as` security-definer operation. That operation remains the sole implementation
of optimistic concurrency, current-admin revalidation, action-state checks, self-confirmation,
last-active-administrator protection, request serialization, idempotent replay, session revocation,
access-version increments, and append-only audit snapshots. Java does not reproduce these rules as
separate read/check/update statements and introduces no production migration.

The signed identity bridge allowlist expands only to the opaque-ID PATCH shape. Assertions remain
bound to the exact method, path, and request ID and are consumed once. The controller independently
requires the ADMIN role, while the database operation rechecks the actor's current durable state.
Responses and problems remain `private, no-store`; metrics contain only bounded outcome labels.

Identity sessions and credentials remain issued by the existing service. Ordinary account
settings, profile/email/password changes, job-market administration, schema ownership, frontend
routing, deployment, production traffic, and CI/CD are unchanged.

Local validation covers all four transitions, disable-only session deletion, audit snapshots,
stale versions, invalid state, self-confirmation, last-admin protection, missing targets, current
actor revalidation, sequential and concurrent idempotency, safe HTTP problems, exact bridge
matching, architecture, and the one-second p95 write budget.
