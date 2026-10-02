# Validation quickstart: private data export

Feature 007 is a code-and-CI migration slice, not a production deployment. The current service retains browser routing, import batches, all writes, schema changes and live traffic.

## Prerequisites

- Java 21, Docker with PostgreSQL 17 Testcontainers support, and the repository's Maven wrapper.
- Synthetic fixtures under `src/test/resources/contracts/exports/` and test-only schema under `src/test/resources/postgres/` after implementation. No production credentials or exports are used.

## Validate the application download independently

Run the feature's application contract, writer, PostgreSQL isolation and HTTP tests from the repository root. Confirm `all`, `filtered`, and `selected` scopes; CSV/XLSX columns and order; stage history; formula-inert text; safe links; empty and invalid selection; mixed owned/foreign IDs; and `private, no-store`. Compare readback against the current-runtime synthetic fixtures described in [research.md](research.md).

## Validate the interview download independently

Run the interview export contract, filename/Markdown/ZIP, PostgreSQL isolation and HTTP tests. Confirm single Markdown versus multi-entry ZIP, distinct ID order, duplicate suppression, collision-safe names, removed-stage fallback, and zero foreign content.

## Run the full gate

```sh
./mvnw verify
```

The full gate must include OpenAPI parsing, signed-bridge failures, read-only/schema safety, safe errors and metrics, architecture, Checkstyle, changed-code ≥80% line/branch coverage, p95 ≤500 ms under the representative 100-record load, fixed query-count checks, large-export temporary-file cleanup, and a Java-only production artifact. The backend and frontend CI plus secret and dependency checks must pass on the PR. Do not deploy or route traffic as part of 007.
