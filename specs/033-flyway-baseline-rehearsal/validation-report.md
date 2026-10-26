# Validation — 2026-10-26

Scope: disposable PostgreSQL 17 containers and synthetic/empty data only. No production database,
deployment, secrets or traffic were accessed.

| Check | Result |
| --- | --- |
| Legacy source | Clean local `bf0ba5478d77a51f1f1aed20fe1293754d28dca3`; remote is behind this revision |
| Migration chain | 66/66 replayed using one transaction per SQL file; 36 public tables |
| Backup/restore | Custom-format `pg_dump`, `pg_restore --list`, fresh-database restore; 36 public tables |
| Schema equivalence | Normalized schema SHA-256 `bc9ad7a5ae5cdd5534c1d10c4dad7c3555ebdf2396f0f10eed7611792b4fce13` on both copies |
| Full restored-schema Flyway baseline | Version `20260929000100`; one successful `BASELINE` row; `validate()` passed; `migrate()` executed 0 migrations; 36 to 37 public tables, with only Flyway history added |
| Automated synthetic-schema test | `FlywayBaselineRehearsalTest` passed with Testcontainers |
| Full Java gate | `./mvnw verify` passed: 745 tests, 0 failures/errors, 1 pre-existing skip; JaCoCo and Checkstyle passed |

The chain contains both `20260927000300_persist_admin_jobs.sql` and
`20260927000300_scheduled_reminders.sql`. They are unique to the legacy runner because it keys by
the complete filename, but conflict as Flyway versions. The rehearsal does not import the
historical files into Flyway or rename files that may already have been applied. It records a
single explicit baseline at the head. No production backup/restore evidence exists, and the
Java default remains `JOBTRACE_FLYWAY_ENABLED=false`.
