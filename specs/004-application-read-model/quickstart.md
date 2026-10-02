# Quickstart: Validate the Application Read Model

This guide verifies feature 004 locally and in CI without deploying or directing production traffic
to the Java runtime.

## Prerequisites

- Java 21
- Docker-compatible Testcontainers runtime
- Repository checkout on `codex/004-application-read-model`
- No production database credentials

## 1. Verify the complete repository

```bash
./mvnw verify
```

Expected results:

- unit, security, contract, PostgreSQL integration, architecture, and performance tests pass;
- line and branch coverage remain at or above 80%;
- Checkstyle and OpenAPI validation pass;
- the embedded frontend and Java-only production artifact still build;
- Flyway remains disabled for the legacy schema.

## 2. Verify the list contract

Run the applications contract and integration tests selected by their package:

```bash
./mvnw -Dtest='com.jobtrace.applications.*ContractTest,com.jobtrace.applications.*QueryIntegrationTest' test
```

Evidence must cover:

- empty and representative pages;
- search plus combined status, type, stage, city, and applied-date filters;
- default ordering and all explicit sorts in both directions;
- offset pages and cursor continuation without duplicates or gaps;
- query defaults, bounds, ignored unknown filters, and malformed cursor rejection;
- exact legacy fixture parity.

## 3. Verify detail and owner isolation

```bash
./mvnw -Dtest='com.jobtrace.applications.*OwnerIsolationIntegrationTest,com.jobtrace.applications.*SecurityTest' test
```

Expected results:

- an owner receives their complete application detail and ordered history;
- another owner's valid identifier and a missing identifier both return the same not-found problem;
- absent, forged, stale, mismatched, and replayed assertions cannot read applications;
- list totals and continuation cursors never incorporate another owner's rows.

## 4. Verify performance

```bash
./mvnw -Dtest='com.jobtrace.applications.*PerformanceTest' test
```

With the documented seeded data set, list and detail p95 must each remain at or below 500 ms. The
test must use a fixed clock and reproducible data, and must not connect to production infrastructure.

## 5. Confirm the read-only boundary

Review the feature contract and application controller surface:

- only `GET /api/applications` and `GET /api/applications/{id}` are introduced;
- no schema migration is added and `JOBTRACE_FLYWAY_ENABLED` remains `false`;
- no create, patch, delete, status, stage, interview-dialog, or export operation is implemented;
- no frontend or legacy route is switched to Java;
- production deployment and canary evidence are not completion requirements for feature 004.
