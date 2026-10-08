# Data Model: Code Hardening

No new persisted entity or schema is introduced.

## Contract row

Legacy method/path, Java method/path, compatibility state (`supported`, `intentional-difference`, `legacy-only`), business invariant and regression evidence. A missing Java route must have an explicit disposition.

## Mutation outcome

The current owner's command may succeed once, be idempotent, conflict on stale version, or fail validation. Database functions and transactions remain the authority for once-only and concurrent transitions; a rejected operation leaves no partial writes.

## Error response

HTTP status, stable machine code, non-sensitive detail and request identifier. Validation errors may include field names, never submitted secrets. Dependency failures are retryable but not reported as success.

## Ownership record

Legacy service owns DDL and marketplace collection/sync-run writes; Java owns only documented business operations against the shared schema. Flyway activation requires separate reviewed evidence and is not a state transition in this stage.
