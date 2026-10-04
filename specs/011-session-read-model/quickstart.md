# Quickstart: Account Session Read Model

## Prerequisites

- Java 21 and the repository Maven wrapper
- Docker-compatible runtime for PostgreSQL integration tests
- No production database, routing, frontend, or deployment changes

## Validate the focused slice

```bash
./mvnw -Dtest='com.jobtrace.identityaccess.*Session*Test' test
```

Expected: contract, owner isolation, expiry, current marking, ordering, read-only, authorization, failure, metrics, and performance tests pass.

## Validate the repository

```bash
./mvnw verify
```

Expected: all tests, JaCoCo thresholds, Checkstyle, architecture checks, and packaging checks pass.

## Manual contract example

Send `GET /api/profile/sessions` with a valid request-bound `Authorization: JobTraceBridge …`, matching `X-Request-Id`, and `X-JobTrace-Session-Id`. Expect a private, non-cacheable JSON array described in [contracts/openapi.yaml](contracts/openapi.yaml). Invalid assertions fail closed; a missing or invalid session header is rejected; no DELETE route is introduced.

## Rollback

Keep the legacy route as production owner. If later validation or activation fails, route the GET back to the legacy implementation; this phase has no data or schema rollback.
