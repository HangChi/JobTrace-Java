# Research: Account Session Read Model

## Current contract

**Decision**: Preserve the existing response as a JSON array with `id`, `createdAt`, `expiresAt`, nullable `userAgent`, and `current`.

**Rationale**: The existing profile UI consumes these fields directly. The legacy query filters `user_id` and `expires_at > now()` and orders newest first.

**Alternatives considered**: A new paged envelope or device-label projection would break compatibility and duplicate client behavior.

## Current-session marking

**Decision**: Require `X-JobTrace-Session-Id` from the trusted legacy server caller, bound to 1-255 characters, and use it only to derive `current` after owner and expiry filtering.

**Rationale**: Signed bridge v1 proves account ownership but intentionally contains no Better Auth session identifier. The legacy caller already knows the authenticated session ID. Forwarding it as internal metadata avoids changing bridge identity semantics; spoofing cannot select another owner's rows and only affects the marker.

**Alternatives considered**: Adding a v2 bridge claim would expand the shared authentication protocol for one derived display field. Omitting `current` would break the existing client contract. Forwarding a cookie would move cookie parsing and session authentication into Java.

## Ordering and time

**Decision**: Read at the database's current instant, order by `created_at desc, id desc`, and serialize UTC timestamps with millisecond precision.

**Rationale**: Database time matches expiry semantics atomically; the tie-breaker makes otherwise equal creation times deterministic while retaining newest-first behavior.

**Alternatives considered**: Application-clock filtering creates database/application clock skew. Unstable tie ordering makes parity tests and UI refreshes unpredictable.

## Ownership and mutation boundary

**Decision**: Implement only GET. Better Auth and the legacy service remain sole session writers and schema owners.

**Rationale**: A read-only first identity slice is independently reversible and avoids moving logout/revocation invariants before cookie and session authority migrate.

**Alternatives considered**: Migrating DELETE in the same slice would transfer a security-sensitive write and current-session logout behavior.

## Diagnostics and failure

**Decision**: Record only bounded operation/outcome/latency metrics; map storage failure through the shared dependency-failure problem handling and set `private, no-store` for this surface.

**Rationale**: Session identifiers and user agents are sensitive. Existing observability patterns provide sufficient diagnosis without recording them.
