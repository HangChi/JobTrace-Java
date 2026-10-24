# Research: Java Migration Code Hardening

## Contract parity

**Decision**: Compare legacy `src/app/api/**/route.ts` with Java MVC mappings by workflow group, recording intentional differences instead of copying implementation details.

**Rationale**: Both services share the business schema, while Java uses signed browser/bridge identity. Copying route code would duplicate rules and obscure ownership differences.

**Alternatives considered**: Blind route generation or proxying to Next.js; rejected because either would bypass Java security/observability or create a second ingestion path.

## Atomicity and errors

**Decision**: Keep owner-aware SQL functions and version checks as the authority for concurrent mutations. Add deterministic HTTP/database regressions and normalize malformed-input and dependency errors only where actual mismatches are observed.

**Rationale**: Java-side read-then-write locking would be weaker and add latency.

**Alternatives considered**: New distributed locks; rejected because PostgreSQL routines already own row-level atomicity.

## Database ownership

**Decision**: Leave Flyway disabled and keep the legacy service as schema and marketplace-ingestion owner. Treat `docs/database-baseline.md` as an activation gate, not approval evidence.

**Rationale**: The candidate migration head was untracked at capture and no reviewed restore/adoption evidence exists.

**Alternatives considered**: Baseline automatically from the candidate hash; rejected because it could claim or replay unapproved DDL.
