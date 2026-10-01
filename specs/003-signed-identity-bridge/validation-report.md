# Validation Report: Signed Identity Bridge

Validation date: 2026-10-02

## Java service

- `./mvnw verify`: PASS
- Tests: 76 run, 0 failures, 0 errors, 1 pre-existing skipped test
- JaCoCo: 95.40% line coverage, 80.82% branch coverage, 95.73% instruction coverage
- Checkstyle: 0 violations
- Architecture rules: PASS
- Embedded Vite frontend build: PASS
- PostgreSQL and Valkey integration coverage: PASS through Testcontainers

The verification suite covers the strict v1 claim set, algorithm and signature rejection,
request binding, issuer and audience validation, lifetime limits, current/previous key
rotation, retired and unknown keys, replay rejection, replay-store outages, concurrent
first-use races, non-disclosing 401 responses, safe logs, metrics, and owner isolation.

## Legacy issuer and canary

The legacy implementation was isolated in `/private/tmp/jobtrace-003-legacy` so the
existing dirty checkout was not modified.

- `corepack pnpm lint`: PASS, with two pre-existing warnings in job-market files
- `corepack pnpm test`: PASS; 96 files and 389 tests
- Coverage: 92.92% statements/lines, 83.12% branches, 92.92% functions
- `corepack pnpm build`: PASS with non-secret build-only placeholder environment values
- `corepack pnpm contract`: PASS; 42 tests
- `corepack pnpm integration`: PASS; 60 tests
- `corepack pnpm performance`: PASS
- `corepack pnpm performance:auth`: PASS; 6 tests
- Targeted bridge coverage: 12 issuer, canary, parity, timeout, and fallback tests

The initial clean-worktree build stopped because the normal database and Better Auth
environment variables were absent. Re-running it with non-secret build-only placeholders
completed successfully; no external database connection was required by the build.

## Cross-service and operational evidence

- Shared fixtures verify valid, forged, replayed, and current/previous-key assertions.
- The Java endpoint and legacy issuer are bound to the same method, path, request ID,
  issuer, audience, owner, role, access version, and 30-second lifetime contract.
- The canary is disabled by default, compares Java and legacy results before serving Java,
  and immediately falls back to the legacy response on mismatch, timeout, or failure.
- The documented rollback disables the canary within five minutes without transferring
  write ownership.
- Replay p95 and analytics read p95 tests satisfy the budgets in the quickstart.
- Java pull request [#15](https://github.com/HangChi/JobTrace-Java/pull/15): backend,
  frontend, dependency review, secret scan, and production-artifact checks passed.
- Legacy pull request [#1](https://github.com/HangChi/JobTrace/pull/1): static, database,
  acceptance, and performance checks passed in
  [workflow run 36871815566](https://github.com/HangChi/JobTrace/actions/runs/36871815566).

## Release status

Implementation and local pre-production verification are complete. The migration slice
remains `VERIFIED`, not `ACTIVE`; production traffic and the authoritative write path
remain with the legacy service.

Remaining release evidence:

1. Deploy through non-public ingress and exercise the operational rollback.
2. Record seven consecutive clean production-canary days.
3. Record an explicit production-active or rolled-back decision and final traffic owner.

## Solo-Maintainer Mode self-review

- **Specification**: The diff implements only the signed identity bridge and the analytics-summary
  canary. The legacy service remains the session authority and sole writer; Java accepts only the
  signed, request-bound read. During review, an unknown database role was found to be silently
  downgraded to `user`; the issuer now fails closed and has a regression test.
- **I. Maintainable Code by Design**: Java preserves `web -> application -> domain`, with Redis as
  an infrastructure adapter. The legacy issuer, fresh-actor lookup, routing, and comparison each
  have one responsibility. Configuration and operational constraints are documented.
- **II. Testing Is a Release Gate**: Unit, filter, integration, contract, owner-isolation,
  concurrency, rotation, performance, and fallback tests pass. Both changed-code coverage floors
  exceed 80%; no test was disabled or weakened.
- **III. Consistent and Accessible User Experience**: The browser continues using the existing
  authenticated analytics route and receives the same response contract. There is no new UI,
  second login, or accessibility-affecting interaction.
- **IV. Measured Performance Budgets**: The suite measures the 25 ms bridge-validation p95 and
  500 ms analytics-read p95 budgets. Canary logs contain bounded classifications and timing only.
- **Delivery and rollback**: The canary is disabled by default, legacy remains the immediate
  fallback, and the documented rollback is a configuration-only change with a five-minute target.
  Both repository pull requests pass CI and security gates. Production activation remains blocked
  on deployment approval and seven clean observation days.

Self-review result: no unresolved standards or specification findings after the fail-closed role
validation correction. No exception to the constitution is requested.
