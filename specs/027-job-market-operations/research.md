# Research: Job Market Operations

## Contract source

**Decision**: Use `src/app/api/job-market`, `src/app/api/admin/job-market`, `src/app/api/internal/job-market` and `src/modules/job-market` in the Next.js repository as the compatibility reference.

**Rationale**: Existing callers already use these routes and the Java marketplace read model follows the same company/campaign projection.

**Alternative considered**: Introduce new routes or response shapes; rejected because the frontend and production routing are outside this stage.

## Persistence

**Decision**: Reuse `job_market_campaign_favorites`, catalog, candidate and sync-run tables, and the existing lease/fencing behavior. Spring JDBC will keep SQL explicit.

**Rationale**: The legacy schema is already deployed and the Java read model uses it. A second write model or JPA mapping would increase drift.

**Alternative considered**: New tables or ORM replacement; rejected as unnecessary migration risk.

## Trust boundaries

**Decision**: Derive the owner and administrator from the trusted bridge principal. Internal jobs must use the existing internal authentication mechanism. Remote URLs require scheme, DNS, IP and redirect checks, size/time bounds, and host allowlists before fetching.

**Rationale**: Catalog and collector operations can otherwise expose private networks or bypass review.

**Alternative considered**: Trust request headers or user-supplied URLs; rejected for privilege and SSRF risk.

## Execution

**Decision**: Keep external collection behind ports and deterministic fake clients in tests. Source sync must acquire and fence a lease before persisting results and must emit sanitized run history.

**Rationale**: This isolates parser/network errors, supports retries and prevents stale workers overwriting newer results.

**Alternative considered**: Fetch during marketplace reads or rely on process-local locking; rejected because it blocks interactive paths and fails across workers.
