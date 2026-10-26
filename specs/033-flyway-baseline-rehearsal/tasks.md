# Tasks

- [x] Pin the legacy revision and manifest; identify the duplicate migration version.
- [x] Replay all 66 migrations into a disposable empty PostgreSQL 17 database.
- [x] Create and restore a disposable custom-format backup; compare normalized schemas.
- [x] Explicitly baseline a restored copy at `20260929000100` and verify zero DDL replay.
- [x] Add an automated synthetic-schema baseline adoption regression test.
- [x] Run `./mvnw verify` with Docker and record the final result.
- [x] Keep Flyway disabled by default and document that production activation remains unapproved.
