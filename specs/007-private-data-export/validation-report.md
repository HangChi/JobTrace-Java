# Feature 007 validation report

## Scope and ownership

This slice adds only two Java GET downloads. The existing Next.js service remains the importer,
sole application/review writer, session issuer, schema owner and sole production traffic owner.
No Java deployment or route activation is scheduled. The signed bridge matcher is opt-in and
restricted to the exact export GET paths; it does not authorize import or mutation routes.

## Compatibility and isolation evidence

- Synthetic legacy-oracle fixtures cover the 13 application columns, Chinese labels, stage
  history ordering, CSV BOM/escaping, XLSX literal cells and HTTP(S)-only hyperlinks. CSV and
  workbook readback tests pass.
- PostgreSQL fixtures contain two owners. All, filtered and selected application reads preserve
  owner isolation; selected IDs cannot widen via filters. Review reads preserve selected order,
  omit foreign/deleted IDs, and join child data only after owner-bound roots. A foreign stage
  occurrence cannot override an owned review's snapshot stage.
- Markdown and ZIP tests cover plain/structured content, safe filenames, duplicate names,
  Unicode text, archive ordering and one-file versus multi-file responses.
- Both HTTP downloads include `private, no-store`; safe problems include a request ID. Export
  failures use fixed messages and do not send private rows or log exception detail. Metrics
  accept only fixed operation/outcome labels.
- Test-only schema is under `src/test/resources/postgres/`. No production migration, import
  handler, writer route, email job or frontend route was added. Read-only tests compare source
  table counts before and after both downloads.

## Resource and performance gates

- The XLSX writer uses a 100-row SXSSF window. Its `close()` calls `dispose()` in the pinned
  POI release; a 1,200-row readback and induced-failure test verify no scratch files remain and
  no rows are silently truncated.
- Representative selected application XLSX and selected review ZIP journeys each pass 10
  warm-up plus 40 measured 100-record samples against the 500 ms p95 budget. Query-shape
  tests assert one application SQL query and three interview SQL queries regardless of
  selected count, avoiding N+1 detail reads.
- Full `./mvnw verify` passed after the final code/security changes with Docker/Testcontainers
  PostgreSQL, Checkstyle, ArchUnit, OpenAPI parsing and Java/frontend packaging. The final
  measured `datatransfer` JaCoCo coverage is 95.8% lines (369/385) and 84.1% branches
  (174/207), above the 80% release gate. There were no Maven test failures.

## Solo-maintainer self-review

The implementation was reviewed against spec FR-001–FR-017 and the four constitution
principles: maintainable ports/adapters, tests as release gates, preserved download experience,
and measured performance. No browser, deployment, schema or production routing changes are
part of this slice. The new `poi-ooxml` dependency is pinned to 5.5.1 and used only for XLSX
serialization. The remaining release step is a PR and required CI/security review; this does
not authorize deployment.

## Final status

Local implementation and verification are complete. GitHub CLI authentication on this machine
currently reports an invalid token, so PR creation and required remote CI/security checks
remain pending. No Java deployment or production traffic activation is scheduled.
