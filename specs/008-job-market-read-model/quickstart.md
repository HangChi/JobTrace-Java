# Validation quickstart: job market read model

Feature 008 is a code-and-CI migration slice, not a production deployment. The current service retains synchronization, all marketplace/favorite/tracking writes, schema changes, browser routing and live traffic.

## Prerequisites

- Java 21, Docker with PostgreSQL 17 Testcontainers support, and the repository Maven wrapper.
- Synthetic legacy JSON fixtures under `src/test/resources/contracts/jobmarket/` and migration-head-compatible test schema under `src/test/resources/postgres/` after implementation.
- No production credentials, marketplace dump, user favorites or tracked applications are used.

## Validate list behavior independently

Run the job-market query/domain, PostgreSQL and HTTP list tests. Confirm defaults, every filter alone and combined, strict bounds, total/page metadata, stable nulls-last ordering, empty and out-of-range pages, default closed exclusion, explicit closed and favorite-only rules, 50-position preview with complete count, safe URLs and two-owner favorite isolation. Compare JSON against synthetic fixtures produced from the current runtime behavior described in [research.md](research.md).

## Validate detail behavior independently

Run the detail query/domain, PostgreSQL and HTTP tests. Confirm campaign-to-company resolution, synced and directory-backed summaries, complete ordered non-closed jobs, active source preference, locations, safe/unavailable application targets, missing/invalid IDs, and two-owner tracked-application isolation.

## Validate security and read-only boundaries

Verify that only the exact list and UUID detail GET paths accept the signed identity bridge. Absent, forged, expired, replayed, wrong-method, wrong-path and ordinary authenticated principals must fail closed. Confirm `private, no-store`, safe problems with request IDs, bounded diagnostics, and unchanged row/schema snapshots after both reads.

## Run the full gate

```sh
./mvnw verify
```

The full gate must include feature/repository OpenAPI parsing, synthetic legacy parity, PostgreSQL owner isolation, URL safety, exact bridge matching, read-only/schema safety, fixed SQL query counts, 10 warm-ups plus 40 list/detail measurements at p95 ≤500 ms, architecture, Checkstyle, at least 80% line/branch coverage, and a Java-only production artifact. Backend/frontend CI and security checks must pass on the pull request. Do not deploy or route traffic as part of 008.
