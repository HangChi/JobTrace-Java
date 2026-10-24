# Research: Administrator Access Changes

## Decision 1: Migrate only the existing account-access command

**Decision**: Add compatibility for `PATCH /api/admin/users/{id}` and its four established actions only.

**Rationale**: Feature 009 explicitly deferred access changes, and the legacy administration surface has exactly one non-job-market mutation route. This is independently valuable and bounded.

**Alternatives considered**: Migrating profile/password/email/session APIs or job-market administration would mix unrelated owners and side effects and is deferred.

## Decision 2: Keep security invariants in the existing atomic database command

**Decision**: Call `public.change_user_access_as` with the verified actor and validated command rather than reimplementing its policy in Java.

**Rationale**: The function atomically locks account state, checks current administrator authority, validates optimistic version and action state, protects the last administrator, requires self-confirmation, changes access, revokes sessions, appends audit state, and returns a stable result. Reusing it prevents split-brain policy during migration.

**Alternatives considered**: Java-side read/check/update sequences are race-prone. New schema or a replacement function would transfer schema ownership and violate scope.

## Decision 3: Preserve database-backed idempotency

**Decision**: Treat the body UUID as the command idempotency key and return the function's `replayed` result. Exercise both sequential and concurrent identical requests and different-payload reuse.

**Rationale**: The current function serializes each request key with a transaction advisory lock and compares a command fingerprint before mutation.

**Alternatives considered**: In-memory or Redis command deduplication would create a second result source and could diverge from durable audit state.

## Decision 4: Keep transport authorization exact and fail closed

**Decision**: Extend the signed bridge allowlist only for PATCH requests whose opaque target segment is 1–128 characters, then require the verified ADMIN role again at the controller boundary.

**Rationale**: Exact method/path binding prevents assertions from authorizing GET, job-market administration, collection routes, or unrelated identity operations. The database function rechecks current actor state to cover changes after assertion issuance.

**Alternatives considered**: Prefix matching or trusting public identity headers would broaden authority. Session migration is outside scope.

## Decision 5: Map stable command failures without diagnostics

**Decision**: Validate malformed bodies as 400, unknown targets as 404, established policy/idempotency conflicts as 409, invalid identities as 401/403, and dependency failures as safe 503 problems. Include audit ID and latest role/disabled/version only for recorded command conflicts.

**Rationale**: This preserves actionable legacy behavior while keeping SQL, stack traces, assertions, reason text, and account details out of diagnostics.

**Alternatives considered**: Returning raw database exceptions leaks internals; collapsing every failure to 500 removes the operator recovery path.

## Decision 6: Validate write performance and bounded effects

**Decision**: Use a deterministic PostgreSQL fixture, warm the operation, measure at least 40 commands/replays, require p95 no more than 1 second, and assert exact version, session, and audit deltas.

**Rationale**: The constitution sets a default interactive write budget of 1 second p95, while correctness requires durable-effect assertions beyond latency.

**Alternatives considered**: Mock-only timing cannot cover locking, JSON result parsing, or transaction behavior.
