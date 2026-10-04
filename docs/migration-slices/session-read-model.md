# Feature 011: account session read model

## Boundary

Feature 011 adds only `GET /api/profile/sessions`. It returns the verified account owner's unexpired sessions with the existing identifiers, timestamps, nullable user-agent value, and current-device marker.

Better Auth and the legacy service remain authoritative for authentication, cookies, session creation, refresh, expiry, revocation, login, logout, registration, credentials, profile data, and schema changes. Java introduces no DELETE handler, migration, frontend change, route activation, deployment change, or CI/CD change.

## Authorization and current marker

The exact GET path uses signed identity bridge v1 and owner scoping from the verified subject. The legacy server caller must additionally forward its authenticated Better Auth session ID in `X-JobTrace-Session-Id` when traffic is eventually activated. This value is limited to 255 characters and only compares against rows already constrained to the verified owner and active expiry window. It cannot choose an owner or expose whether a foreign session exists.

## Persistence contract

The adapter performs one parameterized read from the unchanged `sessions` table:

- `user_id` must equal the verified bridge subject;
- `expires_at` must be later than database `now()`;
- ordering is `created_at desc, id desc`;
- no row is inserted, updated, deleted, locked, refreshed, or revoked.

Timestamps are serialized as UTC ISO strings with millisecond precision. A missing active match for the current-session reference marks every result as non-current.

## Operations

Successful and failed responses use `Cache-Control: private, no-store`. Metrics contain only bounded outcome and latency labels; account IDs, session IDs, user agents, assertions, and returned content are prohibited.

## Activation and rollback

Activation is deliberately unscheduled. Before routing this GET to Java, the legacy issuer must send a request-bound assertion and current-session metadata, and parity must be observed with synthetic or approved non-sensitive traffic. Rollback is routing the GET to the legacy implementation. No data rollback is necessary.
