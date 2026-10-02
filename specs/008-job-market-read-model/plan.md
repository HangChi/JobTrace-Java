# Implementation Plan: Job Market Read Model Migration

**Branch**: `codex/008-job-market-read-model` | **Date**: 2026-10-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/008-job-market-read-model/spec.md`

## Summary

Add authenticated, owner-personalized, read-only compatibility endpoints for the existing job-market company list and campaign detail. Consume the unchanged PostgreSQL company read model maintained by the legacy synchronizer, merge favorite and tracked-application state through owner-bound parameterized SQL, and preserve filtering, ordering, pagination, safe-link and error behavior. Extend the signed identity bridge only for the two exact GET route shapes. The existing service remains the sole synchronizer, writer, schema owner, browser route owner and production traffic owner; no Java deployment is planned.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Existing Spring Boot 4.1.1 Web MVC, Spring Security, Spring JDBC, Jackson and Micrometer; no new production dependency

**Storage**: Existing PostgreSQL 17 job-market company projection, campaign/job/source/location tables, campaign favorites and application links; read-only parameterized SQL; no Flyway activation or production schema change

**Testing**: JUnit 5, MockMvc, Spring Security Test, Testcontainers PostgreSQL 17, ArchUnit, OpenAPI parser, JaCoCo, Checkstyle, synthetic legacy fixtures and existing artifact CI

**Target Platform**: Java 21 Linux production artifact; local and CI verification only, no Java deployment in feature 008

**Project Type**: Spring Boot modular monolith with the existing embedded React/TypeScript frontend artifact unchanged

**Performance Goals**: List and detail each complete within 500 ms p95 after 10 warm-ups and across 40 measured samples, including JSON serialization, under a representative 100-company/100,000-job catalog and owner-personalization load; fixed SQL query-count ceilings

**Constraints**: Trusted signed identity only; exact owner isolation; read-only database access; stable offset pagination contract; HTTPS-only actionable destinations; private no-store responses; no synchronization, administration, favorite/tracking mutation, frontend change, deployment or routing switch

**Scale/Scope**: Two protected GET route shapes; page size 1–100; 50-position list preview with complete count; complete eligible detail job list; shared company data plus per-owner favorites and tracked-application references

## Constitution Check

*GATE: Passed before research and rechecked after design.*

- **I. Maintainable Code by Design**: One `jobmarket` read context with immutable query/projection types, two use cases, a narrow query port, one explicit JDBC adapter and two web reads. Reuse the identity, safe-error and observability infrastructure. No new service, production dependency or cross-context infrastructure dependency.
- **II. Testing Is a Release Gate**: Unit tests cover query normalization, closed-data eligibility, safe URLs and unavailable reasons; PostgreSQL tests cover filters, ordering, pagination, both owner joins and detail eligibility; HTTP tests cover the exact signed bridge, response contract, errors and cache policy. Read-only, query-count, performance, architecture and artifact gates apply, and changed production code must retain at least 80% line and branch coverage.
- **III. Consistent and Accessible User Experience**: No frontend source, navigation or workflow changes. Preserve JSON fields, Chinese unavailable reasons, empty/not-found behavior and existing browser accessibility/responsive coverage. `private, no-store` is a documented privacy hardening over the current owner-keyed 30-second server cache.
- **IV. Measured Performance Budgets**: Both user-visible reads have a reproducible 500 ms p95 gate after 10 warm-ups and 40 measurements, including serialization. Fixed query-count assertions prevent per-company or per-job N+1 behavior.
- **Delivery and review**: Solo-Maintainer Mode still requires a pull request, all CI/security gates, resolved conversations and a written self-review against the specification and all four principles. Production activation and rollback rehearsal remain outside this slice.

Post-design recheck: passed. The design preserves single-writer ownership, needs no new dependency or schema, and requires no constitutional exception.

## Security and Data Boundaries

1. Extend the feature 003 bridge matcher only for exact `GET /api/job-market/campaigns` and `GET /api/job-market/campaigns/{uuid}` requests. Method and normalized path are part of the signed assertion. Bridge enablement remains opt-in; owner identity comes only from `BridgePrincipalOwner`.
2. Treat company/campaign/job/source/location content as shared marketplace data, but bind favorite and application-link joins to the authenticated owner. A caller-supplied header, query value, campaign ID or application ID can never select or widen owner scope.
3. Normalize and validate all filters before SQL. Trim optional text, treat blank values as absent, enforce 100-character bounds, strict status/boolean/ISO-date values and page/limit bounds, then bind every value. Search filters combine with `AND`.
4. Default to the `include_closed=false` company projection. Use `include_closed=true` only for explicit `status=closed` or `favorite=true`, preserving legacy behavior. Resolve detail through an existing campaign and its company projection without revealing private state on missing/ineligible IDs.
5. Return actionable application/source links only when they canonicalize to HTTPS. Non-open jobs return `该岗位已失效`; missing or unsafe open-job targets return `来源未提供安全的官方投递地址`.
6. Mark successful personalized responses `Cache-Control: private, no-store`. Errors use project-safe problem details and request IDs. Logs and metric labels exclude owner IDs, campaign/job/application IDs, filters, URLs, favorite/tracking values and returned content.
7. Java issues only SELECT statements and never invokes projection refresh functions. Test fixtures verify that list/detail create, update and delete no marketplace, favorite, tracking, synchronization, application or schema records.

## Project Structure

### Documentation (this feature)

```text
specs/008-job-market-read-model/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/openapi.yaml
├── checklists/requirements.md
└── tasks.md                 # created later by speckit-tasks, not by this plan
```

### Source Code (planned implementation)

```text
src/main/java/com/jobtrace/jobmarket/
├── domain/                 # immutable criteria, summaries, details, jobs and safe-link rules
├── application/            # list/detail use cases and owner-scoped query port
├── infrastructure/         # parameterized PostgreSQL read adapter and row mapping
└── web/                    # two protected GETs and bounded metrics

src/main/java/com/jobtrace/identityaccess/web/BridgeAuthenticationFilter.java
src/test/java/com/jobtrace/jobmarket/
src/test/resources/contracts/jobmarket/
src/test/resources/postgres/
```

**Structure Decision**: Keep all marketplace reads in one new modular-monolith context. It reads legacy tables directly behind its own port rather than depending on application or identity infrastructure. The only shared integration points are the trusted owner extractor, safe web-error conventions and observability utilities. Existing frontend files remain untouched.

## Verification Stages

1. **Legacy oracle**: Capture current route defaults, filters, closed/favorite rules, company-level aggregation, ordering, 50-position preview, detail job eligibility/order, source preference, URL safety, Chinese reasons and errors. Build synthetic JSON fixtures; use no production data.
2. **Foundational domain**: Add immutable criteria/projections, normalization, include-closed decision, HTTPS target rule and owner-independent query models. Write failing unit and route-security tests first.
3. **List parity**: Add the owner-bound company-projection query and list use case. Verify every filter alone and combined, `favorite=true` selection and `favorite=false` omission semantics, totals, empty/out-of-range pages, null timestamps, stable tie-breaking, closed behavior, favorite isolation, 50-position preview and no writes.
4. **Detail parity**: Add campaign-to-company resolution and a bounded batch job query with active-source selection, locations and owner-bound tracking links. Verify synced/directory summaries, complete ordered eligible jobs, unsafe/unavailable targets, missing IDs and two-owner isolation.
5. **Protected HTTP**: Expose only the two exact GET shapes behind the signed bridge. Verify absent, forged, expired, replayed, method/path-mismatched and ordinary-principal denial; validate safe problems, request IDs, no-store headers and bounded metrics.
6. **Convergence**: Parse feature and repository OpenAPI, compare Java JSON with synthetic legacy fixtures, measure 500 ms p95 and fixed SQL counts, run architecture/coverage/Checkstyle/read-only/artifact gates and `./mvnw verify`, then record Solo-Maintainer self-review and PR CI evidence. Do not deploy or switch traffic.

## Key Risks and Mitigations

- **Projection freshness ownership**: Java consumes a projection refreshed by the legacy synchronizer. Never invoke refresh functions; include stale/absent projection fixtures and keep writer/synchronizer transfer for another feature.
- **Owner-data leakage**: Favorites are company-level through campaigns and tracking markers are job-level. Put owner predicates inside both joins, test two owners sharing the same company/job, and prohibit private identifiers from diagnostics.
- **Compatibility drift in application modes/URLs**: The current list mapper emits `single` or `unavailable`; detail job links have separate unavailable reasons. Record fixtures from runtime source and centralize HTTPS canonicalization instead of trusting stored URLs.
- **Large detail/N+1 behavior**: A company can expose many jobs and locations. Use a fixed number of set-based queries with aggregate locations and assert query counts independently of returned job count.
- **Offset pagination under concurrent sync**: The established contract uses page/limit, so Java preserves it. Deterministic ordering prevents duplicates for an unchanged snapshot; concurrent catalog changes may move rows and are explicitly not upgraded to cursor pagination in this compatibility slice.
- **Read-model/schema coupling**: Explicit column mapping can drift as the legacy schema evolves. Test against a migration-head-compatible PostgreSQL fixture and fail closed on incompatible storage.
- **Scope creep into writes**: Favorite mutations, tracking creation, sync/admin operations and schema changes remain excluded; read-only tests and route inventory checks enforce the boundary.

## Complexity Tracking

No constitution violations require justification. The slice reuses the existing monolith, dependencies, bridge and test infrastructure and introduces neither a deployment unit nor a production schema change.
