# Implementation Plan: Signed Identity Bridge and Analytics Activation

**Branch**: `codex/003-signed-identity-bridge` | **Date**: 2026-10-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-signed-identity-bridge/spec.md`

## Summary

Keep the legacy Next.js service as the session authority and let it exchange a validated session for
a request-bound, 30-second signed assertion sent only on an internal request to the Java analytics
endpoint. Java validates the standard compact JWS, its complete claim set, current or previous key,
request binding, and a one-time identifier claimed atomically in shared Valkey/Redis-compatible
storage before creating a Spring Security principal. The analytics slice then advances through
canary, seven-day observation, rollback rehearsal, and explicit activation evidence without changing
the database schema or write ownership.

## Technical Context

**Language/Version**: Java 21; TypeScript 6 on Node.js 24 in the legacy issuer

**Primary Dependencies**: Spring Boot 4.1.1, Spring Security, Nimbus JOSE JWT, Spring Data Redis,
legacy Next.js 16 server route and Node.js cryptography

**Storage**: Existing PostgreSQL 17 schema remains read-only; Valkey/Redis-compatible ephemeral
storage atomically records accepted assertion identifiers until expiry

**Testing**: JUnit 5, Spring Security Test, Spring Boot Test, Testcontainers PostgreSQL and Valkey,
legacy Vitest contract/unit tests, existing normalized analytics contract fixtures

**Target Platform**: Java 21 Linux container behind trusted internal routing; legacy Next.js server;
managed Valkey/Redis-compatible endpoint reachable by every Java replica

**Project Type**: Two-service migration bridge feeding one modular-monolith Java web application

**Performance Goals**: Analytics read p95 at or below 500 ms including bridge validation; identity
validation p95 at or below 25 ms; rollback within five minutes

**Constraints**: 30-second maximum assertion lifetime; 5-second maximum clock skew; fail closed on
replay-store failure; only `GET /api/analytics/summary`; no public identity headers; no Java schema
migrations; no secrets in logs or source; legacy remains sole writer

**Scale/Scope**: One protected read endpoint, two roles, current plus previous HMAC keys, one shared
replay namespace, one legacy issuer integration, and a seven-day canary window

## Constitution Check

*GATE: Passed before research and rechecked after design.*

- **Maintainable Code**: Verification is isolated behind application ports and one security filter.
  Standard JWS replaces a bespoke token format. Shared replay storage is the only required new
  infrastructure.
- **Testing Gate**: Tests cover parsing, claim boundaries, key rotation, signature validation,
  atomic replay races, owner isolation, contract parity, and fail-closed behavior. Changed code
  remains subject to 80% line and branch coverage.
- **Consistent UX**: Browser requests continue through the existing session-aware route; users see
  no second login, new control, or changed analytics response.
- **Performance Budgets**: Analytics keeps the 500 ms p95 budget and bridge validation adds a 25 ms
  p95 sub-budget measured with representative shared-store latency.
- **Delivery**: Solo-Maintainer Mode still requires a pull request, all automated gates, resolved
  conversations, a written four-principle self-review, rehearsed rollback, and seven clean days.

Post-design recheck: passed. Valkey/Redis-compatible storage is justified because stateless
signatures cannot reject concurrent replay across replicas, and writing replay state into the
unbaselined legacy PostgreSQL schema is prohibited.

## Security and Trust Boundaries

1. The browser presents only its existing session to the legacy service.
2. The legacy server validates session, disabled state, role, and current access version.
3. It creates an HS256 compact JWS using [the v1 contract](contracts/identity-bridge.md).
4. Internal routing forwards the JWS and same UUID request ID to Java; ingress does not expose this
   target directly to browsers.
5. Java validates algorithm, key, signature, issuer, audience, time, request binding, identity, and
   one-time use before establishing authentication.
6. Analytics continues deriving `owner_id` only from the authenticated principal.

The cryptographic check remains mandatory even when network policy is correct. A network allowlist
alone is not identity, and a signature does not replace internal-route policy.

## Observability Expectations

- Count accepted and denied assertions by bounded failure classification only.
- Measure validation and replay-store duration without identity labels.
- Log request ID, non-secret key ID, route, and failure class; never log token, signature, subject,
  session, user data, URL values, or analytics bodies.
- Keep normalized contract mismatch counts separate from authentication failures.
- Readiness fails when the enabled bridge cannot atomically claim replay state.
- Alert on replay, unknown-key spikes, cross-owner failure, mismatch, error-budget breach, or
  analytics p95 above 500 ms.

## Project Structure

### Documentation (this feature)

```text
specs/003-signed-identity-bridge/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/identity-bridge.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code (Java repository)

```text
src/main/java/com/jobtrace/identityaccess/
├── application/ClaimAssertionUseCase.java
├── domain/BridgeIdentity.java
├── domain/ReplayGuard.java
├── infrastructure/RedisReplayGuard.java
└── web/
    ├── BridgeAuthenticationFilter.java
    └── BridgeTokenVerifier.java

src/test/java/com/jobtrace/identityaccess/
├── BridgeAuthenticationFilterTest.java
├── BridgeTokenVerifierTest.java
└── RedisReplayGuardIntegrationTest.java
```

The legacy issuer counterpart belongs in the sibling source repository under
`src/modules/identity-access/infrastructure/identity-bridge.server.ts` and the existing
`src/app/api/analytics/summary/route.ts`. Those changes must be reviewed in that repository
independently because its current working tree contains unrelated work.

**Structure Decision**: Preserve `web -> application -> domain`; the Redis adapter implements a
port. No Java-repository frontend change is needed. The legacy route stays browser-facing until the
broader identity-access migration.

## Release Stages

1. **Implemented**: Java verifier, replay guard, configuration, negative tests, and issuer contract.
2. **Verified**: Legacy issuer integration and cross-repository tests pass; rollback is rehearsed.
3. **Active canary**: A bounded share of this exact route reaches Java with continuous comparison.
4. **Observed**: Seven consecutive days meet security, parity, availability, and latency gates.
5. **Production-active decision**: The maintainer records evidence and updates slice state.

## Complexity Tracking

| Addition | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| Shared Valkey/Redis-compatible replay store | Atomically prevents one-time assertion reuse across replicas without modifying the legacy schema | An in-memory cache permits cross-replica replay; a PostgreSQL table violates the schema-baseline gate |
| Standard JOSE library | Provides reviewed compact-JWS parsing and HMAC verification with algorithm restrictions | Hand-written parsing and signature handling increases security and maintenance risk |
