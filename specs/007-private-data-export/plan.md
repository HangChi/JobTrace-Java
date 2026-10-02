# Implementation Plan: Private Data Export Migration

**Branch**: `codex/007-private-data-export` | **Date**: 2026-10-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/007-private-data-export/spec.md`

## Summary

Add owner-scoped, read-only compatibility downloads for application spreadsheets and interview Markdown/ZIP exports. Preserve the current scope, filter, ordering, field, filename and safety contract using synthetic fixtures. Extend the signed bridge only for the two exact private GET paths. The existing service remains the sole importer, writer, schema owner and production traffic owner; no Java deployment is planned.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Existing Spring Boot 4.1.1 Web MVC, Spring Security, Spring JDBC, Jackson, Micrometer; add one vetted `poi-ooxml` dependency for XLSX writing. Use JDK text and ZIP primitives; no new deployment unit.

**Storage**: Existing PostgreSQL 17 application, stage occurrence, interview review, question and action-item tables; read-only parameterized SQL; no Flyway activation or schema change.

**Testing**: JUnit 5, MockMvc, Spring Security Test, Testcontainers PostgreSQL, workbook readback, ZIP inspection, ArchUnit, OpenAPI parser, JaCoCo and Checkstyle. Synthetic fixtures only.

**Target Platform**: Java 21 Linux production artifact; local and CI verification only, no Java deployment in 007.

**Project Type**: Spring Boot modular monolith with unchanged embedded React/TypeScript frontend artifact.

**Performance Goals**: Each selected-record download ≤ 500 ms p95 for representative loads up to 100 records, including serialization; fixed SQL query-count ceilings. Stress-test uncapped all/filtered application exports for bounded memory and temporary-file cleanup without a new output cap.

**Constraints**: Exact owner isolation; formula-inert spreadsheet cells; safe hyperlink schemes and filenames; private no-store downloads; compatibility with legacy runtime; no import/write path, frontend change, deployment or routing switch.

**Scale/Scope**: Two protected GET downloads; all/filtered/selected application scopes; CSV/XLSX; 1–100 selected interview IDs; single Markdown or multi-file ZIP; no persistent Java export state.

## Constitution Check

*GATE: Passed before research and rechecked after design.*

- **I. Maintainable Code by Design**: One `datatransfer` read context with immutable selections/projections, a small query port, a JDBC adapter, and file writers. Reuse existing identity/safe-error infrastructure. The new XLSX library is justified by interoperable workbook generation; pin and review it during implementation.
- **II. Testing Is a Release Gate**: Unit tests for scopes, filters, filename and formula safety; current-runtime fixtures for CSV/XLSX/Markdown/ZIP; PostgreSQL two-owner and ordering tests; bridge, error, read-only, architecture, performance, and artifact gates. Changed production code must meet ≥ 80% line and branch coverage.
- **III. Consistent and Accessible User Experience**: No browser source or navigation change. Preserve download formats, filenames, Chinese labels, empty/error feedback and current keyboard/accessibility behavior. Adding `private, no-store` to application downloads is deliberate privacy hardening, documented in research.
- **IV. Measured Performance Budgets**: Both selected download journeys have 500 ms p95 budgets under reproducible 100-record workloads, after warm-up and at least 40 samples. SQL counts and large all/filtered memory/temp-file behavior are separately measured.
- **Delivery and review**: Solo-maintainer written self-review, PR and required CI/security checks before merge. No production activation or rollback rehearsal is performed without a separate release decision.

Post-design recheck: passed. No constitution exception is required; imports and writer transfer remain outside the slice.

## Security and Data Boundaries

1. Extend the feature 003 bridge matcher only for exact `GET /api/exports/applications` and `GET /api/exports/interviews`; match method and path before accepting an assertion. Keep bridge enablement opt-in and derive owner only from `BridgePrincipalOwner`.
2. Bind owner to application roots, stage children, review roots, joined application display fields, questions and action items. A foreign selected ID is omitted without disclosing its existence; zero owned results use safe not-found behavior.
3. Parse bounded repeated IDs and filter values before SQL. Parameterize all values. All and selected scopes do not apply filtered criteria; no client ID or query value can widen owner scope.
4. Escape spreadsheet formula prefixes before either format, quote CSV correctly, and permit only HTTP(S) hyperlink targets. Sanitize ZIP entry names and attachment filenames, deduplicate collisions, and avoid path traversal.
5. Make successful responses `private, no-store`. Errors use stable safe problems with request IDs. Logs and metrics never include owner, query, filename, selected ID, exported text or binary bytes.
6. XLSX streaming scratch files are closed and deleted on both success and failure. Java never creates import batches, applications, reviews, or schema objects.

## Project Structure

### Documentation (this feature)

```text
specs/007-private-data-export/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/openapi.yaml
├── checklists/requirements.md
└── tasks.md
```

### Source Code (planned implementation)

```text
src/main/java/com/jobtrace/datatransfer/
├── domain/             # immutable selections, export rows/documents, download metadata
├── application/        # owner-scoped export use cases and read port
├── infrastructure/     # parameterized JDBC adapter and CSV/XLSX/Markdown/ZIP writers
└── web/                # two private GET downloads and bounded metrics

src/main/java/com/jobtrace/identityaccess/web/BridgeAuthenticationFilter.java
src/test/java/com/jobtrace/datatransfer/
src/test/resources/contracts/exports/
src/test/resources/postgres/
```

**Structure Decision**: Keep file-format logic in the data-transfer context and owner-bound reads behind its port. Reuse public domain value/label mappings from applications and interviews only where stable; do not import their infrastructure or introduce reverse dependencies. Existing frontend remains untouched.

## Verification Stages

1. **Legacy oracle**: Record live route defaults, selection validation, filters, column order, stage-history ordering, formula escaping, hyperlink rules, Markdown shape, ZIP names, response headers, and safe errors. Build synthetic CSV/XLSX/Markdown/ZIP fixtures; never use real user exports.
2. **Foundational**: Add immutable selections, output models, safe filename/formula/CSV rules and exact bridge matcher tests. Write failing behavior tests first.
3. **Application export**: Add owner-bound query, all/filtered/selected modes, CSV and XLSX writers, binary/text contract tests, empty/error behavior and read-only checks. Treat all/filtered scalability separately from selected p95.
4. **Interview export**: Add ordered owner-bound batch read, Markdown parity, ZIP packaging, filename safety/collision rules, and single/multi-file HTTP tests.
5. **Convergence**: Verify repository/feature OpenAPI, security and safe-failure paths, query-count and p95 budgets, large-export cleanup, architecture, ≥80% changed-code coverage, Checkstyle, `./mvnw verify`, Java-only production artifact and Solo-Maintainer Mode self-review. Open PR and pass CI; do not deploy.

## Key Risks and Mitigations

- **Unbounded application export**: The legacy all/filtered modes have no row cap. Stream rows and XLSX output with explicit scratch cleanup, measure memory/resource behavior, and avoid silent truncation. If the current contract cannot meet safety/performance, stop for a separately approved compatibility decision.
- **Sensitive file disclosure**: Owner-bind every root/child join; test mixed owned/foreign selected IDs and all/filtered modes. Use no-store responses and no private metric labels.
- **Spreadsheet/ZIP injection**: Preserve formula escaping, safe web hyperlinks and sanitized unique ZIP names; inspect generated bytes in tests.
- **N+1 reads**: Use batch projections rather than repeatedly invoking detail use cases. Assert fixed query counts at 100 selected records.
- **New workbook dependency**: Pin and scan `poi-ooxml`; use only the writer APIs needed, close/dispose the streaming workbook and verify no lingering temporary files.
- **Writer boundary creep**: Tests reject import, mutation, Flyway, frontend and production-routing changes in this feature.

## Complexity Tracking

No constitution violations require an exception. One new library is justified for XLSX interoperability and streaming safety; no service or database migration is added.
