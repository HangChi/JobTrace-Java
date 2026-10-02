# Validation Report: Job Market Read Model Migration

## Phase 3 — Browse Current Recruitment Campaigns

- Synthetic legacy contract fixture and OpenAPI contract parse successfully.
- Domain/default, trusted-owner use case, PostgreSQL projection, two-owner favorite
  isolation, pagination, 50-position preview, complete count, no-store HTTP, and
  ordinary-principal denial tests pass.
- Default reads use only the no-closed projection. An out-of-range page returns
  empty items with the unchanged filtered total.
- The adapter uses two parameterized list queries (count plus page) and does not
  call projection refresh or perform writes.

Later phases append filter, detail, cross-cutting, performance, coverage, and
Solo-Maintainer review evidence here.
