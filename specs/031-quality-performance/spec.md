# Quality and Performance Follow-up

## Goal

Improve the migrated React application without changing the existing routes or API contracts, then measure database read performance before changing SQL.

## Requirements

- Keep the CI coverage gate at 80% for lines, branches, functions, and statements. Add behavior-focused tests for uncovered user and administrator workflows until it passes.
- Preserve keyboard navigation and representative page behavior after visual changes.
- Load page code on demand while keeping compiled assets embedded in the Spring Boot artifact.
- Measure application-list count, filtering, and deep-page queries against representative PostgreSQL data before proposing SQL or index changes. Retain exact totals and pagination semantics unless a separate contract change is approved.
- Do not enable Flyway or modify the legacy database schema as part of performance work.

## Acceptance

- `npm run lint`, `npm test`, `npm run test:coverage`, `npm run build`, and `npm run test:performance` pass.
- Initial JavaScript transfer size is smaller than the pre-change 98.46 KB gzip bundle, with representative routes verified.
- `./mvnw verify` passes in an environment with JVM agent attachment and Docker; the production JAR contains the lazy-loaded page chunks.
- Any database recommendation cites query plans and latency measurements rather than code inspection alone.
