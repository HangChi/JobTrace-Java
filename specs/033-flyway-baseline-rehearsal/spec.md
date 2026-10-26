# Feature specification: isolated Flyway baseline rehearsal

**Status**: Complete for isolated rehearsal; production activation is outside scope

## Goal

With the user's 2026-10-26 approval for an isolated environment only, verify that the pinned
legacy migration chain can be replayed and backed up, and that Flyway can explicitly record its
head without rerunning historical DDL. Keep Java's default Flyway switch disabled.

## Acceptance

- Replay all tracked legacy SQL files into a disposable PostgreSQL 17 database using the legacy
  runner's per-file transaction semantics.
- Restore a disposable archive to a new database and compare normalized schemas.
- Explicitly baseline a restored legacy-shaped database at the pinned head, validate it, and
  verify that `migrate()` runs no SQL and preserves the existing schema.
- Record the duplicate historical version and the absence of production backup/deployment
  evidence; do not modify applied legacy migrations or production routing.
