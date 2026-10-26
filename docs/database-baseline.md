# Legacy database baseline

The Java service treats the existing JobTrace PostgreSQL schema as externally owned. Flyway
must remain disabled until a baseline is reviewed and explicitly approved. Phase 5 introduces
no schema changes and does not create `flyway_schema_history`.

## Candidate snapshot

Recaptured on 2026-10-26 from a clean `/Users/songhangchi/Project/JobTrace` checkout:

| Evidence | Value |
| --- | --- |
| Source Git revision | `bf0ba5478d77a51f1f1aed20fe1293754d28dca3` |
| Source status | Clean; candidate head is tracked |
| Migration count | 66 |
| Candidate head | `supabase/migrations/20260929000100_spring_recruitment_type.sql` |
| Candidate head SHA-256 | `d82ec5da0e0729f0aafad2c38241b3dacb3f149d6c53f8416964c1ce17f66a15` |
| Ordered relative-path manifest | [database-baseline-manifest.sha256](database-baseline-manifest.sha256) |
| Manifest SHA-256 | `c82ff6f14877459fc480f7a47ba09dd58e94f3fb9e90eba1a373e57c84c690f8` |
| Approval status | **Not approved for production**; user approved isolated rehearsal only |
| Approved by / at | User in Codex chat, 2026-10-26, for isolated rehearsal |
| Backup reference | Disposable synthetic PostgreSQL archive, restored and verified on 2026-10-26; no production backup reference |

The earlier 2026-10-01 snapshot came from a dirty checkout with an untracked head migration.
That source-state issue is resolved in this recapture, but the chain contains two files with
the same migration version `20260927000300`:
`20260927000300_persist_admin_jobs.sql` and
`20260927000300_scheduled_reminders.sql`. Flyway cannot import this historical chain as versioned
migrations without resolving that collision. The isolated adoption rehearsal instead uses an
explicit baseline at the legacy head and imports no historical SQL. Do not rename applied legacy
files: the legacy runner identifies each by its complete filename and checksum. The source
revision is still local and ahead of the legacy remote; it must be reconciled before any future
production proposal.

## Isolated rehearsal — 2026-10-26

On disposable PostgreSQL 17 containers, all 66 legacy SQL files replayed into an empty database
when each file ran in one transaction, matching the legacy runner's semantics. The result had 36
`public` tables. A custom-format `pg_dump` archive was restored into a fresh database with the
same 36 tables. Normalized schema dumps matched at SHA-256
`bc9ad7a5ae5cdd5534c1d10c4dad7c3555ebdf2396f0f10eed7611792b4fce13`;
normalization removed only `pg_dump`'s random `\\restrict` markers.

Flyway explicitly baselined a separate restored copy at version `20260929000100`. Validation
passed, `migrate()` executed zero migrations, and the only added public table was
`flyway_schema_history` with one successful `BASELINE` row. The automated
`FlywayBaselineRehearsalTest` repeats the explicit adoption check on a synthetic legacy marker.
This proves the isolated workflow, not production backup recoverability or permission to enable
Flyway in a deployed Java service. See [validation evidence](../specs/033-flyway-baseline-rehearsal/validation-report.md).

## Capture procedure

Run these commands from a clean, pinned legacy checkout:

```bash
git status --short
git rev-parse HEAD
find supabase/migrations -maxdepth 1 -type f -name '*.sql' | sort
find supabase/migrations -maxdepth 1 -type f -name '*.sql' -print0 \
  | sort -z | xargs -0 shasum -a 256
find supabase/migrations -maxdepth 1 -type f -name '*.sql' -print0 \
  | sort -z | xargs -0 shasum -a 256 | shasum -a 256
```

Store the complete ordered manifest with the approval record. Reviewers must verify that the
database backup can be restored, all migrations replay successfully into an empty PostgreSQL
database, and an existing production-shaped database can be adopted without executing legacy
DDL a second time.

## Approval and activation gate

Before enabling Flyway against a production legacy database, record all of the following in a
reviewed change:

1. A clean legacy revision and committed migration head.
2. The full ordered checksum manifest and reviewer identity.
3. Empty-database replay evidence and existing-database adoption evidence.
4. A tested backup/restore reference and maintenance rollback procedure.
5. The exact Flyway baseline version and the environments where it is permitted.

Until that production change is approved, keep `jobtrace.migration.flyway-enabled=false`. The
Java runtime may execute its documented owner/admin business reads and writes against the existing schema,
but must not apply DDL, replay legacy migrations, or claim schema-migration ownership.

## Stage 029 ownership decision

Stage 029 did not activate Flyway. This isolated rehearsal approves no production activation.
The legacy repository continues to own DDL and migration ordering. Java uses the existing schema for documented
owner/admin business operations only; marketplace ATS/WeChat collection and sync-run writes
remain in the legacy service. Setting `JOBTRACE_FLYWAY_ENABLED=true` before the five approval
items above are recorded is an unsupported operator action, not a migration shortcut.
