# Validation: Job Market Operations

## Automated checks

From the Java repository, run `./mvnw verify`. Review the 027 test results and changed-code JaCoCo counters. No frontend or CI/CD command is part of this stage.

## Contract scenarios

1. As owner A, favorite and unfavorite a campaign, including repeated calls; as owner B, verify no state changed.
2. As an administrator, create and edit source metadata, inspect the shared-database catalog, approve or ignore an existing candidate and verify non-admin rejection.
3. Change a company record in the database fixture and verify the Java catalog reflects it without rebuilding bundled assets.
4. Inspect persisted source health and run history; verify Java makes no external collection request or lease claim.

Record actual test totals, changed-code coverage and representative timings before marking the stage implemented. Production cutover and legacy scheduler ownership remain separate decisions.

## Current validation checkpoint (2026-10-08)

The favorite slice (US1), administrator source CRUD, candidate review, static catalog and preliminary sync lease implementation passed local tests. On 2026-10-08 the user narrowed the design to reuse remote database data without Java crawling. The static catalog and lease claimant are being replaced by shared-database reads; the previous validation figures are not a completion result for the revised stage.

## Revised-scope completion checkpoint (2026-10-08)

The catalog now queries the shared company/source/directory tables; Java has no bundled default catalog, external market client, scheduler or lease claimant. Persisted run history remains admin-readable and a Java sync POST returns `410` without creating a run. `./mvnw -q -Dlogging.level.root=OFF -Dspring.main.banner-mode=off verify` passed: 740 tests, 0 failures, 0 errors, 1 skipped. Overall JaCoCo: 95.28% lines and 81.08% branches. The two new database query implementations reached 100% lines and 95.83% branches. A local 1,202-company catalog fixture measured 10 ms p95 for 30 reads, below the 500 ms target. No remote production database was modified or queried during validation.
