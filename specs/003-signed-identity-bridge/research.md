# Research: Signed Identity Bridge and Analytics Activation

## Standard assertion format

**Decision**: Use compact JWS with allowlisted `HS256`, `typ=JWT`, and mandatory `kid`. Java verifies
with Nimbus JOSE JWT; the legacy Node.js server signs the same standard format.

**Rationale**: A reviewed interoperable format avoids inventing parsing and signature semantics. The
payload is a short-lived request assertion, not a browser session or reusable API credential.

**Alternatives considered**: Custom HMAC headers were rejected because canonicalization and
duplicate-header behavior are easy to implement inconsistently. Asymmetric signing is deferred
because this bridge has one issuer and externally distributed symmetric secrets fit its bounded scope.

## Request binding

**Decision**: Bind each assertion to uppercase method, normalized absolute path, and UUID request ID.
Query parameters are excluded because the only allowed route has no query contract.

**Rationale**: A stolen assertion cannot move to another path or request, and the exact path is
unambiguous.

**Alternatives considered**: Subject plus expiry permits cross-route reuse. Signing a complete URL is
brittle under proxy host/scheme rewriting and query ordering.

## Replay prevention

**Decision**: Atomically claim `issuer + jti` in shared Valkey/Redis-compatible storage using
set-if-absent with a TTL covering remaining validity plus skew. A duplicate, timeout, or exception
denies authentication.

**Rationale**: This enforces one-time use across replicas without changing the legacy PostgreSQL
schema. Replay records contain random identifiers, not user data.

**Alternatives considered**: Local caches fail across replicas. PostgreSQL storage is blocked until
schema ownership is approved. Permitting replay inside 30 seconds violates the required control.

## Key rotation

**Decision**: Configure a key-ID-to-secret map and one explicit active issuer key. Java accepts only
configured current/previous IDs; removing an ID retires it. Secrets decode to at least 32 random bytes.

**Rationale**: `kid` enables bounded overlap without downtime or trial verification against secrets.

**Alternatives considered**: One unversioned secret causes rotation outages; trying every secret
obscures key selection and safe diagnostics.

## Access-version freshness

**Decision**: The legacy issuer reads current disabled state, role, and `access_version` after session
validation and signs them. Java uses these values only after complete assertion validation.

**Rationale**: Account changes affect new assertions immediately, while the 30-second lifetime bounds
already-issued authorization.

**Alternatives considered**: Reading Better Auth cookies in Java couples it to session internals.
Java querying identity tables duplicates ownership and broadens the slice.

## Browser and routing integration

**Decision**: Keep the browser-facing Next.js route. It validates the session, issues the assertion
server-side, calls Java internally, and returns the unchanged response. Routing can switch fully back
to the local legacy use case.

**Rationale**: Browsers never receive bridge credentials and retain the current URL, cookie, schema,
and error behavior.

**Alternatives considered**: Direct browser-to-Java traffic requires a separate browser auth
migration. Forwarding raw cookies expands secret exposure and couples Java to Better Auth.

## Canary evidence

**Decision**: Compare normalized JSON first with synthetic/pre-production shadow traffic and then a
bounded production canary. Retain only mismatch class, implementation label, duration, status class,
source revision, and request ID.

**Rationale**: This supports diagnosis without retaining owner identifiers or response bodies.

**Alternatives considered**: Logging response bodies exposes unnecessary personal data. Immediate
full cutover provides neither observation nor safe rollback.
