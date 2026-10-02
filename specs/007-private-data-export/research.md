# Research: Private Data Export Migration

## Decision 1: First data-transfer slice is export-only

**Decision**: Cover the existing application and interview download routes. Keep import preview, batch persistence, confirmation, cleanup, and all business writes in the current service.

**Rationale**: Export reads existing owned data and emits a file. Import preview creates a persisted batch, and confirmation writes applications/events, so mixing it into 007 would cross the single-writer boundary established in features 004–006. No Java deployment or traffic activation is planned.

**Alternatives considered**: Migrate import and export together (larger write and schema risk); application CSV alone (too narrow to cover the existing two export journeys).

## Decision 2: Runtime source is the compatibility oracle

**Decision**: Derive exact behavior from `src/app/api/exports/applications/route.ts`, `src/app/api/exports/interviews/route.ts`, `src/modules/data-transfer/application/export-applications.ts`, `export-interviews.ts`, `infrastructure/spreadsheet-writer.ts`, `src/modules/interviews/application/interview-markdown.ts`, and current contract/integration tests in the legacy repository. Use synthetic content and byte/structure fixtures, never production exports.

**Rationale**: Application export defaults to `xlsx` and `filtered`, supports `all|filtered|selected`, validates at most 100 selected IDs, applies filters only in `filtered`, and returns 404 for no owned result. It has a UTF-8 BOM for CSV and a fixed Chinese-column order. Interview export requires 1–100 IDs, deduplicates while preserving selection order, returns Markdown for one owned review or ZIP for several, and has sanitized collision-safe filenames. The current route does not set a cache policy on application downloads; Java should use `private, no-store` as a deliberate privacy hardening, with contract tests otherwise preserving the runtime behavior.

**Alternatives considered**: Treat the older feature specification as byte-perfect truth (risks drift); record real user files (privacy risk).

## Decision 3: Keep export queries dedicated and owner-bound

**Decision**: Add a `datatransfer` read context with explicit export-selection models and one owner-bound query adapter. Reuse application/interview domain labels where stable, but avoid calling one-detail-per-row use cases for multi-file exports. Batch roots and children with bounded query counts, preserving legacy sort and owner checks on every join.

**Rationale**: Existing 004/005 read adapters have pagination or per-ID detail contracts not suited to all/filtered exports or 100-review selections. Calling them repeatedly would create avoidable N+1 reads and makes the 500 ms p95 target harder to meet. Dedicated projections remain read-only and do not depend on either module's infrastructure.

**Alternatives considered**: Reuse the existing list/detail use cases directly (pagination/N+1 and contract mismatch); one giant cross-domain service (weak boundaries).

## Decision 4: File encoding and safety

**Decision**: Write CSV with UTF-8 BOM, stable headers and RFC-style quoted cells; prefix text beginning `=`, `+`, `-`, `@`, tab, or carriage return with an apostrophe, matching the current writer. Build XLSX through a vetted `poi-ooxml` dependency using its streaming workbook for large all/filtered scopes. Preserve literal text cells and allow hyperlinks only for HTTP(S) URLs. Close/dispose workbook and remove its temporary files on success and failure. Generate Markdown as UTF-8 and ZIP with the Java standard library, using safe unique entry names and deterministic selected order.

**Rationale**: A maintained workbook library is less error-prone than implementing Office Open XML manually. [Apache POI's official spreadsheet guide](https://poi.apache.org/components/spreadsheet/how-to.html) describes SXSSF's bounded row window and mandatory temporary-file cleanup; this informs the implementation and tests. The current service's formula escaping and hyperlink restrictions are security-relevant compatibility behavior.

**Alternatives considered**: Hand-written OOXML (less dependency cost but substantially more correctness risk); in-memory XSSF for every export (high memory use for unrestricted all/filtered exports); adding a ZIP library (unnecessary for simple archives).

## Decision 5: Performance and failure boundaries

**Decision**: Capture one clock for filenames, use deterministic representative datasets of up to 100 selected records for p95 ≤ 500 ms and fixed query-count checks, and separately stress all/filtered streaming behavior and cleanup without inventing an output truncation. Reject invalid selected IDs early, fail closed for absent identity/storage, return established validation/not-found responses and safe request IDs, and never log or metric-label exported content.

**Rationale**: Selected exports have an explicit 100-ID contract, while all/filtered scopes are uncapped in the legacy service. Treating them as equivalent load cases would hide a scaling risk. File generation, not just SQL, belongs in the measured path.

**Alternatives considered**: Impose a new all/filtered row cap (breaks portability contract); measure only query time (misses serialization and archive cost).
