# Validation Report: Java Migration Code Hardening

**Date**: 2026-10-08
**Branch**: `codex/029-code-hardening`

## Automated evidence

- `./mvnw verify -q` completed successfully with Docker Desktop and local Testcontainers PostgreSQL 17. Surefire reports show 754 tests, zero failures/errors and one conditional skip. JaCoCo and Checkstyle passed.
- The single skip is `PackagedApplicationTest`, conditional on `jobtrace.packaged.jar`. It was run separately against `target/jobtrace-0.1.0-SNAPSHOT.jar` with `./mvnw -Djobtrace.packaged.jar=target/jobtrace-0.1.0-SNAPSHOT.jar -Dtest=PackagedApplicationTest test -q` and passed, including the restored `/api/health` alias.
- JaCoCo class counters for changed production classes: `HealthController` 16/16 lines and 4/4 branches; `GlobalExceptionHandler` 29/29 lines and 2/2 branches. The repository-wide coverage gate passed. No stage-028 coverage threshold was modified.
- `RouteParityInventoryTest` checks all 65 pinned legacy API route files have exactly one disposition. `MigrationActivationGateTest` and `LegacySchemaSafetyTest` check Flyway remains disabled and no schema history is created by synthetic startup. `ApplicationCommandIntegrationTest` exercises concurrent stale-version updates against PostgreSQL and a create-to-private-read HTTP journey; `ClaimAssertionUseCaseTest` covers concurrent replay rejection.
- `GlobalErrorHttpContractTest` checks malformed JSON and missing parameters return a safe 400 with request ID and `private, no-store`. `BrowserAuthenticationIntegrationTest` rejects an ordinary browser session at the admin route. Existing stage tests cover origin, CSRF, owner isolation and business workflows as indexed in [behavior evidence](contracts/behavior-evidence.md).
- `git diff --check` passed. No CI/CD or frontend-028 implementation files were changed.

## Ownership and deliberate differences

The [route matrix](contracts/route-matrix.md) records legacy-only ATS/WeChat collection and sync-run writing, plus explicit Better Auth and readiness differences. Java reads and curates shared marketplace data but does not collect it. `docs/database-baseline.md` remains **Not approved**; this stage neither enables Flyway nor claims schema ownership. The local tests never used the remote production database.

## Deferred manual acceptance

The user's live browser and shared-remote-data checks are listed in [quickstart.md](quickstart.md). They were intentionally not claimed as passed here. Production route cutover, deployment and schema baseline approval remain separate decisions.
