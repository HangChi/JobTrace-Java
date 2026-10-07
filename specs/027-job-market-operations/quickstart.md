# Validation: Job Market Operations

## Automated checks

From the Java repository, run `./mvnw verify`. Review the 027 test results and changed-code JaCoCo counters. No frontend or CI/CD command is part of this stage.

## Contract scenarios

1. As owner A, favorite and unfavorite a campaign, including repeated calls; as owner B, verify no state changed.
2. As an administrator, create and edit a source, inspect catalog health, discover a candidate, approve or ignore it and verify non-admin rejection.
3. Research a company and scan an ATS board using deterministic fake pages; reject private IPs, unsafe redirects and oversized pages.
4. Collect a WeChat article into reviewable candidates; retry and verify deduplication and provenance.
5. Synchronize a due source, inspect its run, then overlap or expire a lease and verify stale writes are fenced.

Record actual test totals, changed-code coverage and representative timings before marking the stage implemented. Production cutover and scheduler enablement are separate decisions.

## Current validation checkpoint (2026-10-08)

The favorite slice (US1) and administrator source list/create/update slice (part of US2) passed HTTP/PostgreSQL integration tests. Full `./mvnw -q -Dlogging.level.root=OFF -Dspring.main.banner-mode=off verify` passed: 715 tests, 0 failures, 0 errors, 1 skipped. The currently added 027 production files have 204/205 covered lines (99.5%) and 123/128 covered branches (96.1%). Default catalog/bootstrap, candidate review, external intake and sync are not yet implemented or benchmarked; these figures are not a completion result for the full stage.
