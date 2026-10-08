# Research: Job Market Operations

## Contract source

**Decision**: Use `src/app/api/job-market`, `src/app/api/admin/job-market`, `src/app/api/internal/job-market` and `src/modules/job-market` in the Next.js repository as the compatibility reference.

**Rationale**: Existing callers already use these routes and the Java marketplace read model follows the same company/campaign projection.

**Alternative considered**: Introduce new routes or response shapes; rejected because the frontend and production routing are outside this stage.

## Persistence

**Decision**: Reuse `job_market_campaign_favorites`, company/source/candidate and sync-run tables in the already configured remote PostgreSQL database. Spring JDBC will keep SQL explicit. The old service keeps ownership of ingestion and leases.

**Rationale**: The legacy schema is already deployed and the Java read model uses it. A second write model or JPA mapping would increase drift.

**Alternative considered**: Bundled JSON defaults, new tables or ORM replacement; rejected because current remote rows are the source of truth and duplicated catalogs drift.

## Trust boundaries

**Decision**: Derive the owner and administrator from the trusted bridge principal. Java performs no external marketplace fetch. Manually submitted source URL metadata still requires approved HTTPS hosts.

**Rationale**: Java cannot expose private networks through collection when it does not perform collection; administrator metadata must still be constrained before persistence.

**Alternative considered**: Trust request headers or user-supplied URLs; rejected for privilege and SSRF risk.

## Execution

**Decision**: The existing service remains the single marketplace ingestion writer. Java only reads persisted source/candidate/run data and performs explicit user/admin curation mutations.

**Rationale**: Shared remote data avoids a second crawler, duplicate leases and conflicting writers while keeping Java browsing current.

**Alternative considered**: Fetch during marketplace reads or port the legacy worker; rejected because both duplicate existing ingestion and increase operational risk.
