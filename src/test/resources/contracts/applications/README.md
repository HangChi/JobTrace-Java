# Application read-model legacy fixtures

These fixtures freeze the normalized JSON contract produced by the legacy JobTrace application
repository and HTTP routes for feature 004.

## Provenance

- Source contracts: `src/modules/applications/application/contracts.ts`
- Query normalization: `src/modules/applications/application/list-query.ts`
- Row mapping and ordering: `src/modules/applications/infrastructure/postgres-application-repository.ts`
- HTTP routes: `src/app/api/applications/route.ts` and
  `src/app/api/applications/[id]/route.ts`
- Baseline reviewed: 2026-10-02

UUIDs, timestamps, and dates are deterministic synthetic values. No production or personal data is
present. JSON object keys retain the public camel-case response names. Arrays retain contract order;
no field is removed during comparison.

## Fixtures

- `empty-page.legacy.json`: authenticated owner with no matching applications.
- `representative-page.legacy.json`: one page item covering nullable values, stage aggregation,
  follow-up derivation, version, and a continuation cursor.
- `representative-detail.legacy.json`: the same application with notes, ordered stage occurrences,
  and newest-first event history.

## Regeneration

Create equivalent synthetic rows in an isolated legacy test database, freeze the Shanghai business
date at `2026-10-02`, call the two GET routes as the fixture owner, and serialize `response.json()`
with two-space indentation and a trailing newline. Review changes against the TypeScript contracts
and repository mapper before replacing a fixture.
