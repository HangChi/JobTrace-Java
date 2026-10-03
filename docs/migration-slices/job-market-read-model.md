# Feature 008: job-market read model

Feature 008 adds two private Java reads: `GET /api/job-market/campaigns` and
`GET /api/job-market/campaigns/{campaignId}`. The signed identity bridge remains
disabled by default. This feature does not deploy Java, change frontend routing, or
activate production traffic.

The existing JobTrace service remains the sole owner of browser sessions, marketplace
collection and synchronization, projection refresh, campaign/favorite/application-link
writes, PostgreSQL schema changes, administration, and production traffic. Java consumes
the unchanged legacy-owned company projection and related job tables with parameterized
SELECT statements only. There is no production Flyway migration or job-market mutation
endpoint in this repository.

## Contract, identity, and privacy

- A verified `BridgeIdentity` is the only source of owner identity. Assertions are bound
  to the exact GET method, exact list or UUID-shaped detail path, request ID, expiry, and
  single-use replay claim. Public owner headers and ordinary principals are rejected.
- List favorite overlays and detail application links are always owner-bound. Shared
  company and job content cannot reveal another owner's private state.
- List filters preserve the current keyword, company, location, status, inclusive date,
  favorite, closed-projection, pagination, and stable ordering behavior. List positions
  are a 50-item preview while detail positions are complete.
- Detail uses active sources only, prefers official then most recently seen sources,
  excludes closed jobs, deduplicates locations, and never performs N+1 reads.
- Application and source destinations are returned only after HTTPS canonicalization.
  Stale jobs use `该岗位已失效`; unsafe open targets use
  `来源未提供安全的官方投递地址`.
- Successful personalized responses use `private, no-store`. Errors contain a request ID
  and generic detail without identities, filters, company/campaign IDs, URLs, SQL, or
  returned content. Metrics use bounded operation/outcome labels only.

## Validation and performance

Run `./mvnw verify` with Java 21 and Docker. The feature contract, synthetic legacy JSON,
and test-only PostgreSQL 17 schema under `specs/008-job-market-read-model/` and
`src/test/resources/` are the executable compatibility oracle.

The reproducible performance test contains exactly 100 companies and 100,000 jobs with
two owners. It performs 10 warm-ups followed by 40 list and 40 detail samples including
JSON serialization. Both p95 budgets are 500 ms; list is fixed at two SQL reads and detail
at three. The suite also covers contract parity, owner isolation, signed-bridge failures,
safe dependency failure, read-only/schema guarantees, architecture, and coverage.

Local and CI verification plus Solo-Maintainer self-review are the completion criteria.
Deployment, traffic cutover, production observation, and rollback rehearsal are explicitly
unscheduled. If a later release is approved, rollback should route both reads back to the
existing service; the legacy writer, schema, and projection refresher remain untouched.
