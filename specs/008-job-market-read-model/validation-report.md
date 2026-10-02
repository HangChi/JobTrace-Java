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
