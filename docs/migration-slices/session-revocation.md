# Feature 012: account session revocation

## Boundary

Feature 012 adds only `DELETE /api/profile/sessions` beside feature 011's read route. An authenticated owner can delete one session by opaque identifier and receives `{ "revoked": true }` only after one owned row is removed.

Better Auth and the legacy service remain authoritative for authentication, cookies, session creation, refresh, expiry, login, logout, registration, credentials, profile data, bulk revocation, and schema changes. Java introduces no migration, frontend change, route activation, deployment change, or CI/CD change.

## Authorization and privacy

The exact DELETE path uses signed identity bridge v1. Its assertion is bound to DELETE, the exact path, and request ID, consumed once, and converted to an owner subject. Public headers and ordinary principals cannot select an owner. Missing, already-revoked, and foreign identifiers all return the same `not_found` problem, so ownership is not disclosed.

## Persistence contract

The adapter executes one parameterized atomic operation against the unchanged `sessions` table:

```sql
delete from sessions
where id=:sessionId and user_id=:ownerId
returning id
```

Identifiers are case-sensitive and limited to 255 characters. There is deliberately no expiry predicate, matching the established behavior for an expired row that still exists. Concurrent attempts can therefore produce only one returned row and one success.

## Operations

Successful and failed responses use `Cache-Control: private, no-store`. Metrics contain only bounded outcome and latency labels; account IDs, session IDs, user agents, assertions, request bodies, and SQL details are prohibited. The representative p95 budget is one second.

## Activation and rollback

Activation is unscheduled. Before routing DELETE to Java, the legacy issuer must send a DELETE-bound assertion and parity must be observed with approved non-sensitive traffic. Rollback routes DELETE back to the legacy implementation. No data or schema rollback is necessary.
