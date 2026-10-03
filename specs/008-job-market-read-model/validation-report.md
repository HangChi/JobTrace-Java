# Validation Report: Job Market Read Model Migration

## Phase 3 — Browse Current Recruitment Campaigns

- Synthetic legacy contract fixture and OpenAPI contract parse successfully.
- Domain/default, trusted-owner use case, PostgreSQL projection, two-owner favorite
  isolation, pagination, 50-position preview, complete count, no-store HTTP, and
  ordinary-principal denial tests pass.
- Default reads use only the no-closed projection. An out-of-range page returns
  empty items with the unchanged filtered total.
- The adapter uses two parameterized list queries (count plus page) and does not
  call projection refresh or perform writes.

Later phases append filter, detail, cross-cutting, performance, coverage, and
Solo-Maintainer review evidence here.

## Phase 4 — Find Relevant Opportunities

- Parameter tests cover trimming, blank omission, multilingual and mixed-case text,
  the 100-character boundary, canonical status/boolean/date values, and pagination
  bounds. `favorite=false` retains the default no-closed projection.
- PostgreSQL tests prove conjunctive keyword/company/location/status/date filtering,
  inclusive date behavior, exact totals, stable out-of-range pages, explicit closed
  selection, and owner-isolated favorites. Inactive sources cannot independently
  create list rows because the legacy-owned company projection remains the sole list
  source.
- HTTP tests cover all accepted filters together and verify malformed values return
  a generic problem with a request ID and without reflecting private filter text.
- `MarketplaceQueryParametersTest`, `JobMarketFilterQueryIntegrationTest`, and
  `JobMarketFilterControllerTest` pass (12 tests, zero failures). The adapter still
  executes only the two SELECT statements established in Phase 3.

## Phase 5 — Inspect Campaign Jobs

- Synced and directory-backed detail responses match the representative legacy
  fixture fields. Detail summaries retain complete positions; directory rows do not
  synthesize jobs from a different campaign type.
- The PostgreSQL adapter resolves campaign to company, selects the current no-closed
  projection, and loads eligible jobs, deduplicated locations, preferred active
  official sources, and owner-bound tracking links with three fixed set-based reads.
- Closed jobs and inactive sources are excluded. Stale and unsafe application targets
  use the specified Chinese unavailable reasons; unsafe source and application URLs
  are never returned.
- Two-owner tests prove tracked-application and favorite state isolation while shared
  job content remains identical. Missing and ineligible campaigns map to a safe 404.
- Contract, use-case, query, isolation, and controller suites pass (16 tests, zero
  failures), including UUID validation, ordinary-principal denial, no-store headers,
  and safe storage-unavailable responses without partial detail content.

## Phase 6 — Release gates

- `./mvnw verify` passed on Java 21 with Docker and PostgreSQL 17 Testcontainers.
  Surefire ran 297 tests with zero failures/errors and one pre-existing skip; the
  packaged-application integration test also passed. Both the feature and repository
  OpenAPI contracts parse and assert only the two protected job-market GET operations.
- The full run covers synthetic fixture parity, two-owner favorite/tracking isolation,
  absent/forged/expired/path- and method-mismatched/replayed identity assertions,
  validation and storage failures, request IDs, redacted diagnostics, bounded metrics,
  private no-store responses, read-only row/schema checks, and architecture direction.
- Checkstyle reports zero violations and `git diff --check` passes. JaCoCo reports
  96.67% overall line coverage (2,088/2,160) and 83.45% overall branch coverage
  (812/973). Changed job-market and bridge production classes have 96.00% line
  coverage (336/350) and 82.95% branch coverage (146/176), above both 80% gates.
- The reproducible performance fixture contains 100 companies, 100,000 jobs, and two
  owners. After 10 warm-ups, 40 serialized list and 40 serialized detail samples
  measured p95 at 1 ms and 9 ms respectively, below the 500 ms budget. SQL query
  counts remain fixed at two for list and three for detail.
- The Maven lifecycle installs and builds the React/TypeScript frontend with zero
  reported npm vulnerabilities. `./scripts/test-production-artifact.sh` passed:
  the Spring Boot image runs as uid/gid 10001, contains Java but no Node/npm runtime,
  serves the SPA and deep link, and passes liveness/readiness checks.
- Test data, identities, URLs, and schemas are synthetic. No production migration,
  deployment, traffic routing, synchronization, favorite/tracking mutation, or schema
  ownership change is authorized by this evidence.

## Solo-Maintainer Mode self-review

- **Specification**: FR-001–FR-020 and SC-001–SC-007 are covered by the three
  independent story suites and the cross-route security, compatibility, isolation,
  performance, failure, schema, and read-only gates. The only new HTTP surface is the
  authenticated list and UUID detail GET pair; the review found no missing behavior,
  incorrect implementation, or out-of-scope production authority.
- **I — Maintainable code**: immutable domain projections, two focused application
  use cases, a parameterized Spring JDBC adapter, and a thin web boundary preserve
  `web -> application -> domain`, with infrastructure implementing the query port.
  Safe-link and query-normalization rules are centralized; no new dependency or
  deployment unit was added. Architecture and static checks pass.
- **II — Testing**: domain, use-case, HTTP, JSON contract, OpenAPI, signed-identity,
  PostgreSQL integration, owner-isolation, failure, architecture, read-only,
  performance, and packaged-artifact tests pass. Changed-code line and branch coverage
  exceed 80%; no feature test was disabled or weakened.
- **III — Consistent and accessible UX**: no frontend source, route, copy, keyboard,
  accessibility, or responsive behavior changed. The API retains established fields,
  Chinese unavailable reasons, ordering, pagination, empty/error semantics, and
  private cache behavior, while the existing frontend build remains green.
- **IV — Measured performance**: the documented representative data set, warm-ups,
  samples, serialization boundary, 500 ms p95 limits, and constant query budgets are
  automated and pass. This is reproducible local evidence, not a production claim.

The self-review found no standards or constitution exception and no unresolved local
finding. A pull request and its required CI/security results will be recorded below.
Deployment and production traffic activation remain explicitly unscheduled.
