# Research: Account Session Revocation

## Existing behavior

**Decision**: Preserve `DELETE /api/profile/sessions` with JSON `{ "id": string }`, success `{ "revoked": true }`, and a not-found problem when no owned row is deleted.

**Rationale**: The existing client removes the returned session locally and redirects to login when it revoked the session previously marked current.

**Alternatives considered**: A path parameter or empty success response would require frontend contract changes.

## Ownership and privacy

**Decision**: Delete by both verified owner ID and requested session ID, returning the deleted ID only internally to distinguish success from not found.

**Rationale**: One conditional mutation prevents foreign deletion and gives missing, foreign, and already-revoked identifiers the same observable result.

**Alternatives considered**: Reading before deleting creates a time-of-check/time-of-use race and can leak foreign existence.

## Retry and concurrency

**Decision**: Preserve non-idempotent legacy semantics: the first successful deletion returns success; later or concurrent attempts return not found.

**Rationale**: The existing command has no idempotency key and uses `DELETE ... RETURNING`. PostgreSQL serializes competing deletes so only one can return a row.

**Alternatives considered**: Treating an absent row as success changes the established client-visible contract.

## Current and expired sessions

**Decision**: Do not filter by expiry or prohibit deletion of the session used to issue the bridge assertion.

**Rationale**: The legacy command revokes any owned row. Self-revocation invalidates subsequent Better Auth use while allowing the delete response to complete.

**Alternatives considered**: Active-only deletion leaves expired rows behind and diverges from current behavior. Preventing self-revocation removes an existing safety control.

## Diagnostics

**Decision**: Add bounded success, invalid, not-found, denied-identity, dependency-failure, and failure outcomes with a one-second budget.

**Rationale**: Operators can distinguish command outcomes without recording account or session data.
