# Research: Job Market Read Model Migration

## Decision 1: Make 008 a bounded private read slice

**Decision**: Migrate only authenticated campaign list and campaign detail reads. Keep synchronization, source discovery, collection, normalization, approval, lifecycle closing, administration, favorite mutation, tracking/application creation, sessions, schema ownership and production routing in the current service.

**Rationale**: The two reads form a complete browse-to-detail journey and can consume the existing company projection without creating a second writer. Favorite and tracked-application fields are projections of existing owner data, not mutation authority.

**Alternatives considered**: Move the whole job-market context (too many schedulers, external adapters and writers); migrate list only (does not complete the user journey); include favorite mutation (crosses the established single-writer boundary).

## Decision 2: Runtime source and tests are the compatibility oracle

**Decision**: Derive behavior from the legacy `campaign-service.ts`, `contracts.ts`, `postgres-campaign-query.ts`, `apply-target.ts`, company-read-model migration and current contract/integration tests. Capture synthetic JSON fixtures rather than production responses.

**Rationale**: Runtime behavior is more precise than older prose. It establishes page 1/limit 20 defaults, 1–100 page sizes, trimmed 100-character filters, strict status/date/boolean parsing, conjunctive filtering, company-level totals, nulls-last stable ordering, 50-position previews and owner-specific fields. Only `favorite=true` narrows the result; `favorite=false` has the same selection semantics as omission. Detail first resolves a campaign to its company, returns the company summary, then returns non-closed jobs with an active source ordered by publication descending/nulls last, title and ID.

**Alternatives considered**: Reconstruct behavior solely from UI expectations (misses storage and isolation details); copy production data (privacy and repeatability risk).

## Decision 3: Consume, but never maintain, the existing company projection

**Decision**: Query `job_market_company_read_models` with `job_market_companies` for list/summary data. Use `include_closed=false` by default and `include_closed=true` only when `favorite=true` or `status=closed`. Merge favorites at query time through owner-bound campaign relationships. Never call `refresh_job_market_company_read_model` or `rebuild_job_market_company_read_models`.

**Rationale**: The projection already provides company-level positions, complete count, locations, lifecycle state, representative campaign, source and timestamps, plus indexes for browse/status/search/location. Recomputing it in Java would duplicate the legacy synchronizer's writer rules and weaken consistency.

**Alternatives considered**: Aggregate raw posts on every list request (more expensive and duplicates projection logic); recreate the projection in Java (creates a second writer); cache Java results (adds invalidation and owner-leak risks without a demonstrated need).

## Decision 4: Use set-based, owner-bound detail reads

**Decision**: Resolve campaign-to-company, load the company summary, and fetch eligible jobs with locations, best active source and `application_job_market_links` in a fixed number of parameterized queries. Bind owner only to favorites and tracking links. Do not call one query per job or location.

**Rationale**: Shared marketplace content is not owner-owned, but personalization is. The legacy best source orders official sources first, then most recently seen. A set-based design preserves behavior, prevents N+1 reads and makes query ceilings enforceable.

**Alternatives considered**: Load every job and then query its locations/tracking separately (N+1); reuse the applications module's infrastructure (wrong context direction and unnecessary coupling); expose owner in request criteria (spoofing risk).

## Decision 5: Centralize destination safety and preserve unavailable reasons

**Decision**: Canonicalize actionable application and source targets to HTTPS at the domain/web boundary. A non-open job is unavailable with `该岗位已失效`; an open job with a missing or unsafe target uses `来源未提供安全的官方投递地址`. Unsafe or missing campaign targets produce `applyMode=unavailable` and a null primary URL. The current company projection emits list/detail summary modes `single` or `unavailable`; `select` remains a contract enum for compatibility but is not synthesized by this slice.

**Rationale**: Stored projection and source values are inputs, not proof that a link is safe. Centralizing the rule prevents controllers and row mappers from diverging and preserves established user feedback.

**Alternatives considered**: Return stored URLs verbatim (unsafe); reject the entire campaign for one bad link (unnecessarily removes valid shared content); add HTTP redirects to the allowlist (broadens the contract).

## Decision 6: Do not reproduce the legacy server cache

**Decision**: Return `Cache-Control: private, no-store` and do not add Java response/application caching in 008. Depend on the indexed PostgreSQL projection and measure the complete path.

**Rationale**: The legacy list uses a 30-second owner-keyed server cache, but list and detail contain private favorite/tracking state. A no-store policy removes shared-cache leakage and invalidation complexity. It is a deliberate privacy hardening that does not change visible content.

**Alternatives considered**: Shared response caching (privacy risk); owner-keyed local caching (multi-instance invalidation and staleness); Redis caching (new dependency and invalidation surface before a measured need).

## Decision 7: Reuse the signed bridge with exact route matching

**Decision**: Extend `BridgeAuthenticationFilter` only for `GET /api/job-market/campaigns` and a UUID-shaped detail path. Assertions remain method-, path-, request-ID-, time- and replay-bound; Java derives the owner from the verified bridge principal.

**Rationale**: Features 003–007 already establish the internal identity boundary. Exact route matching prevents a valid assertion from authorizing excluded favorite, sync or admin operations that share the `/api/job-market` prefix.

**Alternatives considered**: Protect the entire prefix (accidentally broadens authority); accept an owner header/query parameter (spoofable); add a second authentication mechanism (unnecessary complexity).

## Decision 8: Test performance and read-only behavior explicitly

**Decision**: Seed a deterministic PostgreSQL 17 fixture aligned to the legacy migration head, including 100 companies, 100,000 jobs, mixed statuses/sources/locations and two-owner personalization. After 10 warm-ups, collect 40 list and 40 detail timings including MockMvc serialization and require p95 ≤500 ms. Assert a fixed query ceiling and compare before/after row/schema state.

**Rationale**: The constitution requires the user-visible path, not SQL alone, to meet the budget. The existing legacy performance script uses 100 companies/100,000 posts but only nine SQL samples; 008 strengthens this to the feature's explicit sampling criteria and covers owner joins plus serialization.

**Alternatives considered**: Benchmark SQL alone (misses mapping/serialization); use small unit fixtures for the performance gate (not representative); rely on elapsed time without query counts (can hide N+1 growth).
