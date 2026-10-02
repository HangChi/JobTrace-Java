# Quickstart: Validate the Private Interview Read Model

This is the validation guide for future implementation of feature 005. The feature is currently at the **task-planning stage**: the routes and tests described below are not yet implemented in Java. No production credentials, deployment, or traffic change are required.

## Prerequisites

- Java 21, the repository's Maven wrapper, and Docker for Testcontainers PostgreSQL.
- A clean checkout containing feature 005 implementation when it is built.
- No legacy production database or live service connection. Test identities and review data must be synthetic.

## Local verification after implementation

From the Java repository root, run:

```sh
./mvnw verify
```

The expected result is a successful backend test suite, Checkstyle, JaCoCo, OpenAPI/contract validation, architecture checks, the existing frontend build, and a Java artifact containing the compiled frontend. The CI `backend`, `frontend`, `production-artifact`, `dependency-review`, and `secret-scan` jobs must also pass before merge. The repository's coverage floor is 80% for both lines and branches of changed production code.

## Scenarios to prove

1. **Private list**: With two owners and a mixed set of assessment/interview reviews, verify empty and populated pages; search by company, position, and question text; combine application, status, stage, result, date, and publication filters. Check total, default limit, strict invalid-value responses, descending date/UUID order, and full cursor traversal exactly once.
2. **Private detail**: Verify an owned review with ordered questions and action items, an empty-child review, and a review whose stage occurrence has been removed. Missing and cross-owner review IDs must return the same not-found status and code.
3. **Application dialog**: Verify feature 004 application detail remains unchanged and the added interview summaries are ordered and owner-scoped. An owned application with no reviews yields `interviews: []`; missing and cross-owner applications yield indistinguishable not-found outcomes.
4. **Identity and failure paths**: Verify missing, forged, expired, path-mismatched, and replayed bridge assertions fail on each route; an ordinary authenticated principal without `BridgeIdentity` must also fail. A database outage yields a safe problem response. No private content or identifier enters logs or metric labels.
5. **Performance and artifact**: Use fixed synthetic data with documented row and child counts, warm up, then measure at least 40 samples per operation. List, detail, and dialog p95 must each be at most 500 ms; assert bounded query counts to catch N+1 behavior.

The exact JSON shapes are in [contracts/openapi.yaml](contracts/openapi.yaml), and entity and ownership rules are in [data-model.md](data-model.md). Success fixtures should be compared structurally with synthetic legacy responses. For error parity, compare HTTP status and stable error code while retaining Java's existing safe problem envelope.

## Release boundary

Passing local and CI checks is the feature 005 completion criterion. The existing JobTrace service remains the sole session, schema, write, publication, and production traffic owner. Java deployment, routing, canary observation, and rollback rehearsal require a separate future release decision.
